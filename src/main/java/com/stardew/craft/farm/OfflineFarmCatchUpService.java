package com.stardew.craft.farm;

import com.stardew.craft.Config;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.core.ModDimensions;
import com.stardew.craft.core.ModGameRules;
import com.stardew.craft.core.ModMiningDimensions;
import com.stardew.craft.manager.CropGrowthManager;
import com.stardew.craft.manager.SprinklerManager;
import com.stardew.craft.manager.TreeGrowthManager;
import com.stardew.craft.time.StardewTimeManager;
import com.stardew.craft.time.settlement.DailySettlementServices;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.TimeUnit;

@EventBusSubscriber(modid = StardewCraft.MODID)
public final class OfflineFarmCatchUpService {
    private static final int MAX_ITEM_FAILURES = 3;
    private static final Map<MinecraftServer, OfflineFarmCatchUpService> SERVICES =
            new WeakHashMap<>();

    private final MinecraftServer server;
    private final Map<UUID, ActiveJob> jobsByOwner = new LinkedHashMap<>();
    private final Map<UUID, PendingJob> pendingByOwner = new LinkedHashMap<>();
    private final ArrayDeque<UUID> pendingQueue = new ArrayDeque<>();
    private final ArrayDeque<UUID> roundRobin = new ArrayDeque<>();
    private final DeferredLeaseCleanup deferredLeaseCleanup =
            new DeferredLeaseCleanup();

    private OfflineFarmCatchUpService(MinecraftServer server) {
        this.server = server;
    }

    public static synchronized void enqueue(ServerLevel level, UUID playerId) {
        if (level == null || playerId == null
                || !ModDimensions.STARDEW_VALLEY.equals(level.dimension())) {
            return;
        }
        SERVICES.computeIfAbsent(level.getServer(), OfflineFarmCatchUpService::new)
                .enqueuePlayer(level, playerId);
    }

    public static synchronized boolean isPlayerLocked(ServerPlayer player) {
        if (player == null || (!ModDimensions.STARDEW_VALLEY.equals(player.level().dimension())
                && !ModMiningDimensions.STARDEW_MINING.equals(player.level().dimension()))) {
            return false;
        }
        OfflineFarmCatchUpService service = SERVICES.get(player.server);
        if (service == null) {
            return false;
        }
        UUID owner = FarmInstanceRegistry.get().getOwnerForPlayer(player.getUUID());
        return owner != null && (service.jobsByOwner.containsKey(owner)
                || service.pendingByOwner.containsKey(owner));
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        OfflineFarmCatchUpService service;
        synchronized (OfflineFarmCatchUpService.class) {
            service = SERVICES.get(event.getServer());
        }
        if (service != null) {
            service.tick();
        }
    }

    @SubscribeEvent
    public static synchronized void onServerStopping(ServerStoppingEvent event) {
        OfflineFarmCatchUpService service = SERVICES.remove(event.getServer());
        if (service != null) {
            service.clear();
        }
    }

    private void enqueuePlayer(ServerLevel level, UUID playerId) {
        FarmInstanceRegistry registry = FarmInstanceRegistry.get();
        UUID owner = registry.getOwnerForPlayer(playerId);
        if (owner == null || jobsByOwner.containsKey(owner)
                || pendingByOwner.containsKey(owner)) {
            return;
        }
        DailySettlementServices.Services settlement =
                DailySettlementServices.find(level.getServer());
        if (DailySettlementServices.ownsSettlement(settlement, playerId)) {
            return;
        }
        FarmInstance farm = registry.getFarm(owner);
        int targetDay = OfflineFarmCatchUp.computeAbsoluteDay();
        if (farm == null || !farm.isInitialized() || farm.getLastOnlineDay() >= targetDay) {
            return;
        }

        pendingByOwner.put(owner, new PendingJob(level, playerId, owner));
        pendingQueue.addLast(owner);
    }

    private void createPendingJob(PendingJob pending) {
        FarmInstanceRegistry registry = FarmInstanceRegistry.get();
        DailySettlementServices.Services settlement =
                DailySettlementServices.find(pending.level.getServer());
        if (DailySettlementServices.ownsSettlement(settlement, pending.playerId)) {
            return;
        }
        FarmInstance farm = registry.getFarm(pending.owner);
        int targetDay = OfflineFarmCatchUp.computeAbsoluteDay();
        if (farm == null || !farm.isInitialized() || farm.getLastOnlineDay() >= targetDay) {
            return;
        }

        grantSeasonGrace(pending.level, farm, pending.playerId, registry);
        CropGrowthManager cropMgr = CropGrowthManager.get(pending.level);
        TreeGrowthManager treeMgr = TreeGrowthManager.get(pending.level);
        SprinklerManager sprinklerMgr = SprinklerManager.get(pending.level);
        OfflineFarmCatchUpPlan plan = OfflineFarmCatchUpPlan.create(
                pending.level.dimension(), farm.getFarmBoundsMin(), farm.getFarmBoundsMax(),
                cropMgr.getAllCropPositions(), treeMgr.getAllSaplingPositions(),
                sprinklerMgr.getAllSprinklerPositions());

        OfflineFarmCatchUpCursor<GlobalPos> cursor = new OfflineFarmCatchUpCursor<>(
                farm.getLastOnlineDay(), targetDay,
                plan.crops(), plan.trees(), plan.sprinklers(),
                operations(pending.level, registry, farm, cropMgr, treeMgr));
        jobsByOwner.put(pending.owner,
                new ActiveJob(pending.level, farm, cursor, targetDay));
        roundRobin.addLast(pending.owner);
        StardewCraft.LOGGER.info(
                "[FARM-CATCHUP] Queued farm {} from day {} through {} ({} crops, {} trees, {} sprinklers)",
                pending.owner, farm.getLastOnlineDay(), targetDay,
                plan.crops().size(), plan.trees().size(), plan.sprinklers().size());
    }

    private OfflineFarmCatchUpCursor.Operations<GlobalPos> operations(
            ServerLevel level,
            FarmInstanceRegistry registry,
            FarmInstance farm,
            CropGrowthManager cropMgr,
            TreeGrowthManager treeMgr) {
        return new OfflineFarmCatchUpCursor.Operations<>() {
            @Override
            public void growCrop(int absoluteDay, GlobalPos position) {
                withItemLease(level, position, 0,
                        () -> OfflineFarmCatchUp.growCropOneDay(level, cropMgr, position));
            }

            @Override
            public void growTree(int absoluteDay, GlobalPos position) {
                withItemLease(level, position, 8,
                        () -> OfflineFarmCatchUp.growTreeOneDay(level, treeMgr, position));
            }

            @Override
            public void waterSprinkler(GlobalPos position) {
                withItemLease(level, position, 2,
                        () -> OfflineFarmCatchUp.waterSprinkler(level, position));
            }

            @Override
            public void commitDay(int absoluteDay) {
                if (farm.getLastOnlineDay() < absoluteDay) {
                    farm.setLastOnlineDay(absoluteDay);
                    farm.setLastOnlineSeason(
                            OfflineFarmCatchUp.seasonOfAbsDay(absoluteDay));
                    registry.setDirty();
                }
            }
        };
    }

    private void withItemLease(
            ServerLevel level, GlobalPos position, int radius, Runnable operation) {
        TemporaryChunkLeaseTracker.Lease lease =
                FarmChunkManager.get().acquireTemporaryChunks(
                        level, FarmChunkManager.chunkPositionsForPosition(
                                position.pos(), radius));
        deferredLeaseCleanup.run(lease, operation);
    }

    private void tick() {
        DailySettlementServices.Services settlement = DailySettlementServices.find(server);
        if (settlement != null && settlement.coordinator().isActive()) {
            return;
        }
        long budgetNanos = TimeUnit.MILLISECONDS.toNanos(
                Config.DAILY_SETTLEMENT_BUDGET_MILLIS.get());
        long deadline = System.nanoTime() + budgetNanos;
        int itemLimit = Config.DAILY_SETTLEMENT_ITEM_LIMIT.get();
        int processed = 0;
        if (!pendingQueue.isEmpty() && System.nanoTime() < deadline) {
            UUID owner = pendingQueue.removeFirst();
            PendingJob pending = pendingByOwner.remove(owner);
            if (pending != null) {
                long startedAt = System.nanoTime();
                try {
                    createPendingJob(pending);
                } catch (RuntimeException | Error failure) {
                    StardewCraft.LOGGER.error(
                            "[FARM-CATCHUP] Failed to create catch-up job for farm {}",
                            owner, failure);
                }
                long elapsed = System.nanoTime() - startedAt;
                if (elapsed > budgetNanos) {
                    StardewCraft.LOGGER.warn(
                            "[FARM-CATCHUP] Catch-up job creation exceeded tick budget: farm={}, elapsedMs={}",
                            owner, TimeUnit.NANOSECONDS.toMillis(elapsed));
                }
                processed++;
            }
        }
        while (processed < itemLimit && !roundRobin.isEmpty()
                && System.nanoTime() < deadline) {
            deferredLeaseCleanup.retryOne();
            UUID owner = roundRobin.removeFirst();
            ActiveJob job = jobsByOwner.get(owner);
            if (job == null) {
                continue;
            }
            if (job.farm.getLastOnlineDay() >= job.targetDay) {
                jobsByOwner.remove(owner);
                StardewCraft.LOGGER.info(
                        "[FARM-CATCHUP] Discarded stale catch-up job for farm {} at day {}",
                        owner, job.farm.getLastOnlineDay());
                continue;
            }
            processed++;
            try {
                job.cursor.runNext();
                job.failures = 0;
            } catch (RuntimeException | Error failure) {
                handleFailure(owner, job, failure);
            }
            if (job.cursor.isComplete()) {
                jobsByOwner.remove(owner);
                StardewCraft.LOGGER.info(
                        "[FARM-CATCHUP] Catch-up complete for farm {} through day {}",
                        owner, job.targetDay);
                enqueuePlayer(job.level, owner);
            } else {
                roundRobin.addLast(owner);
            }
        }
    }

    private void handleFailure(UUID owner, ActiveJob job, Throwable failure) {
        job.failures++;
        if (job.failures >= MAX_ITEM_FAILURES && job.cursor.canSkipFailedItem()) {
            StardewCraft.LOGGER.error(
                    "[FARM-CATCHUP] Skipping failed item for farm {} after {} attempts",
                    owner, job.failures, failure);
            job.cursor.skipFailedItem();
            job.failures = 0;
            return;
        }
        StardewCraft.LOGGER.warn(
                "[FARM-CATCHUP] Farm {} step failed (attempt {}): {}",
                owner, job.failures, failure.toString());
    }

    private static void grantSeasonGrace(
            ServerLevel level,
            FarmInstance farm,
            UUID playerId,
            FarmInstanceRegistry registry) {
        int currentSeason = StardewTimeManager.get().getCurrentSeason();
        if (farm.getLastOnlineSeason() == currentSeason) {
            return;
        }
        int graceDays = level.getServer().getGameRules()
                .getInt(ModGameRules.RULE_CROP_GRACE_PERIOD_DAYS);
        if (graceDays <= 0) {
            return;
        }
        farm.setGraceDaysLeft(graceDays);
        registry.setDirty();
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(playerId);
        if (player != null) {
            player.sendSystemMessage(Component.translatable(
                    "stardewcraft.farm.grace_period.granted", graceDays));
        }
    }

    private void clear() {
        int retries = deferredLeaseCleanup.pendingCount();
        for (int index = 0; index < retries; index++) {
            deferredLeaseCleanup.retryOne();
        }
        jobsByOwner.clear();
        pendingByOwner.clear();
        pendingQueue.clear();
        roundRobin.clear();
    }

    private static final class ActiveJob {
        private final ServerLevel level;
        private final FarmInstance farm;
        private final OfflineFarmCatchUpCursor<GlobalPos> cursor;
        private final int targetDay;
        private int failures;

        private ActiveJob(
                ServerLevel level,
                FarmInstance farm,
                OfflineFarmCatchUpCursor<GlobalPos> cursor,
                int targetDay) {
            this.level = level;
            this.farm = farm;
            this.cursor = cursor;
            this.targetDay = targetDay;
        }
    }

    private static final class PendingJob {
        private final ServerLevel level;
        private final UUID playerId;
        private final UUID owner;

        private PendingJob(ServerLevel level, UUID playerId, UUID owner) {
            this.level = level;
            this.playerId = playerId;
            this.owner = owner;
        }
    }
}
