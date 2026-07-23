package com.stardew.craft.time.settlement;

import com.stardew.craft.core.ModDimensions;
import com.stardew.craft.core.ModMiningDimensions;
import com.stardew.craft.network.overnight.OvernightSettlementPayload;
import com.stardew.craft.network.overnight.OvernightSettlementTracker;
import com.stardew.craft.player.PassOutService;
import com.stardew.craft.player.PlayerStardewData;
import com.stardew.craft.player.PlayerStardewDataAPI;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public final class PlayerDailySettlementService {
    private final SettlementBackend backend;
    private final PendingStore pending;
    private final Map<UUID, DailySettlementBarrier.ReadyResult> readyResults =
            new ConcurrentHashMap<>();

    public PlayerDailySettlementService(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        this.backend = new ProductionSettlementBackend(server);
        this.pending = new PlayerDataPendingStore(
                com.stardew.craft.player.PlayerDataManager::getPlayerData,
                () -> com.stardew.craft.player.PlayerDataManager.get().setDirty());
    }

    PlayerDailySettlementService(SettlementBackend backend, PendingStore pending) {
        this.backend = Objects.requireNonNull(backend, "backend");
        this.pending = Objects.requireNonNull(pending, "pending");
    }

    public boolean participates(ServerPlayer player) {
        Objects.requireNonNull(player, "player");
        return (player.level().dimension() == ModDimensions.STARDEW_VALLEY
                || player.level().dimension() == ModMiningDimensions.STARDEW_MINING)
                && com.stardew.craft.farm.FarmInstanceRegistry.get().hasFarm(player.getUUID());
    }

    public DailySettlementWorkUnit createDailyWorkUnit(DailySettlementContext context) {
        Objects.requireNonNull(context, "context");
        return DailySettlementWorkUnits.cursor(
                "player_daily_settlement",
                context.playerIds(),
                UUID::toString,
                playerId -> settlePlayer(context, playerId),
                () -> {});
    }

    void settlePlayer(DailySettlementContext context, UUID playerId) {
        Optional<OvernightSettlementPayload> payload = settleIfOnline(context, playerId);
        if (payload.isEmpty()) {
            pending.save(context, playerId);
            readyResults.putIfAbsent(playerId, pendingReadyResult(context));
            return;
        }
        readyResults.put(playerId,
                new DailySettlementBarrier.ReadyResult(context.absoluteDay(), payload.orElseThrow()));
    }

    private Optional<OvernightSettlementPayload> settleIfOnline(
            DailySettlementContext context, UUID playerId) {
        @SuppressWarnings("unchecked")
        Optional<OvernightSettlementPayload>[] result = new Optional[] {Optional.empty()};
        try {
            DailySettlementDateView.run(
                    context, () -> result[0] = backend.settleIfOnline(context, playerId));
        } catch (RuntimeException | Error failure) {
            throw failure;
        } catch (Exception failure) {
            throw new IllegalStateException("Unable to settle player " + playerId, failure);
        }
        return result[0];
    }

    private static OvernightSettlementPayload settleOnlinePlayer(
            DailySettlementContext context, ServerPlayer player) {
        if (player.isCreative()) {
            PlayerStardewDataAPI.cureExhaustion(player);
            PlayerStardewDataAPI.restoreEnergy(player, PlayerStardewDataAPI.getMaxEnergy(player));
            com.stardew.craft.mastery.MasteryBuffLifecycle.clearAllDailyMasteryBuffs(player);
        } else {
            PlayerStardewDataAPI.sleep(player, context.sleepMinute());
            PassOutService.applyCombatDeathEnergyPenalty(player);
        }
        PlayerStardewDataAPI.setHealth(player, PlayerStardewDataAPI.getMaxHealth(player));

        com.stardew.craft.shop.BlacksmithService.onNewDay(player);
        com.stardew.craft.shop.BlacksmithService.showToolUpgradeNotification(player);

        OvernightSettlementPayload settlementPayload = OvernightSettlementTracker.consumePayload(player);
        PlayerStardewDataAPI.recordOvernightShippedItems(player, settlementPayload.shippedItems());
        List<PlayerStardewData.SkillLevelUp> appliedLevelUps =
                PlayerStardewDataAPI.applyPendingSkillLevelUps(player);
        PlayerStardewDataAPI.applySkillLevelRecipeUnlocks(player, appliedLevelUps);
        if (!appliedLevelUps.isEmpty()) {
            PlayerStardewDataAPI.restoreEnergy(player, PlayerStardewDataAPI.getMaxEnergy(player));
            PlayerStardewDataAPI.setHealth(player, PlayerStardewDataAPI.getMaxHealth(player));
        }

        com.stardew.craft.player.PlayerDataEventHandler.syncPlayerData(
                player, com.stardew.craft.player.PlayerDataManager.getPlayerData(player));
        com.stardew.craft.quest.StardewQuestEvents.fireDayStarted(player, context.absoluteDay());
        com.stardew.craft.mastery.MasteryOnboardingService.checkOnMorning(player);
        return buildPayload(context, player.getUUID(), settlementPayload, appliedLevelUps);
    }

    private static OvernightSettlementPayload buildPayload(
            DailySettlementContext context,
            UUID playerId,
            OvernightSettlementPayload settlementPayload,
            List<PlayerStardewData.SkillLevelUp> appliedLevelUps) {
        List<OvernightSettlementPayload.LevelUpData> levelUps =
                new ArrayList<>(settlementPayload.levelUps());
        for (PlayerStardewData.SkillLevelUp levelUp : appliedLevelUps) {
            levelUps.add(new OvernightSettlementPayload.LevelUpData(
                    levelUp.skill().getId(), levelUp.newLevel()));
        }

        PassOutService.PassOutResult passOutResult =
                PassOutService.consumePassOutResult(playerId, context.absoluteDay());
        int passOutType = passOutResult == null ? -1 : passOutResult.type().getId();
        int moneyLost = passOutResult == null ? 0 : passOutResult.moneyLost();
        List<net.minecraft.world.item.ItemStack> lostItems =
                passOutResult == null ? List.of() : passOutResult.lostItems();
        return new OvernightSettlementPayload(
                context.absoluteDay(),
                settlementPayload.shippedItems(),
                List.copyOf(levelUps),
                passOutType,
                moneyLost,
                lostItems);
    }

    public DailySettlementBarrier.ReadyResult readyResult(UUID playerId, int absoluteDay) {
        DailySettlementBarrier.ReadyResult result = readyResults.get(playerId);
        return result != null && result.absoluteDay() == absoluteDay ? result : null;
    }

    DailySettlementBarrier.ReadyResult readyResultOrCreate(
            DailySettlementContext context, UUID playerId) {
        DailySettlementBarrier.ReadyResult result = readyResult(
                playerId, context.absoluteDay());
        if (result != null) {
            return result;
        }
        pending.save(context, playerId);
        DailySettlementBarrier.ReadyResult fallback = pendingReadyResult(context);
        readyResults.put(playerId, fallback);
        return fallback;
    }

    Optional<DailySettlementBarrier.ReadyResult> onLogin(
            UUID playerId, DailySettlementBarrier barrier) {
        Objects.requireNonNull(playerId, "playerId");
        Optional<PendingSettlement> scheduled = pending.find(playerId);
        if (scheduled.isEmpty()) {
            return Optional.empty();
        }
        DailySettlementContext context = scheduled.orElseThrow().context(playerId);
        Optional<OvernightSettlementPayload> payload = settleIfOnline(context, playerId);
        if (payload.isEmpty()) {
            return Optional.empty();
        }
        DailySettlementBarrier.ReadyResult result = new DailySettlementBarrier.ReadyResult(
                context.absoluteDay(), payload.orElseThrow());
        readyResults.put(playerId, result);
        if (barrier != null) {
            DailySettlementBarrier.ReadyResult retained =
                    barrier.readyResult(playerId, context.absoluteDay());
            if (retained != null && !barrier.replaceReady(playerId, result)) {
                throw new IllegalStateException(
                        "Unable to replace retained settlement result for " + playerId);
            }
        }
        pending.clear(playerId, context.absoluteDay());
        return Optional.of(result);
    }

    public void onLogout(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
    }

    public void clear() {
        readyResults.clear();
    }

    Optional<PendingSettlement> pendingSettlement(UUID playerId) {
        return pending.find(playerId);
    }

    static Optional<DailySettlementBarrier.ReadyResult> recoverPending(ServerPlayer player) {
        Objects.requireNonNull(player, "player");
        return new PlayerDailySettlementService(player.server)
                .onLogin(player.getUUID(), null);
    }

    private static DailySettlementBarrier.ReadyResult pendingReadyResult(
            DailySettlementContext context) {
        return new DailySettlementBarrier.ReadyResult(
                context.absoluteDay(),
                new OvernightSettlementPayload(context.absoluteDay(), List.of(), List.of()));
    }

    interface SettlementBackend {
        Optional<OvernightSettlementPayload> settleIfOnline(
                DailySettlementContext context, UUID playerId);
    }

    interface PendingStore {
        Optional<PendingSettlement> find(UUID playerId);

        void save(DailySettlementContext context, UUID playerId);

        void clear(UUID playerId, int absoluteDay);
    }

    static final class PlayerDataPendingStore implements PendingStore {
        private final Function<UUID, PlayerStardewData> playerData;
        private final Runnable markPersistent;

        PlayerDataPendingStore(
                Function<UUID, PlayerStardewData> playerData, Runnable markPersistent) {
            this.playerData = Objects.requireNonNull(playerData, "playerData");
            this.markPersistent = Objects.requireNonNull(markPersistent, "markPersistent");
        }

        @Override
        public Optional<PendingSettlement> find(UUID playerId) {
            return data(playerId).getPendingDailySettlement().map(PendingSettlement::fromData);
        }

        @Override
        public void save(DailySettlementContext context, UUID playerId) {
            PlayerStardewData.PendingDailySettlement scheduled =
                    new PlayerStardewData.PendingDailySettlement(
                            context.absoluteDay(), context.year(), context.season(), context.day(),
                            context.sleepMinute(), context.seasonChanged());
            if (data(playerId).schedulePendingDailySettlement(scheduled)) {
                markPersistent.run();
            }
        }

        @Override
        public void clear(UUID playerId, int absoluteDay) {
            if (data(playerId).clearPendingDailySettlement(absoluteDay)) {
                markPersistent.run();
            }
        }

        private PlayerStardewData data(UUID playerId) {
            return Objects.requireNonNull(playerData.apply(playerId), "player data " + playerId);
        }
    }

    record PendingSettlement(
            int absoluteDay,
            int year,
            int season,
            int day,
            int sleepMinute,
            boolean seasonChanged) {

        private static PendingSettlement fromData(
                PlayerStardewData.PendingDailySettlement pending) {
            return new PendingSettlement(
                    pending.absoluteDay(), pending.year(), pending.season(), pending.day(),
                    pending.sleepMinute(), pending.seasonChanged());
        }

        private DailySettlementContext context(UUID playerId) {
            return new DailySettlementContext(
                    absoluteDay, year, season, day, sleepMinute, seasonChanged,
                    List.of(playerId), Set.of());
        }
    }

    private static final class ProductionSettlementBackend implements SettlementBackend {
        private final WeakReference<MinecraftServer> server;

        private ProductionSettlementBackend(MinecraftServer server) {
            this.server = new WeakReference<>(server);
        }

        @Override
        public Optional<OvernightSettlementPayload> settleIfOnline(
                DailySettlementContext context, UUID playerId) {
            MinecraftServer current = server.get();
            if (current == null) {
                throw new IllegalStateException("Daily settlement server is no longer available");
            }
            ServerPlayer player = current.getPlayerList().getPlayer(playerId);
            return player == null
                    ? Optional.empty()
                    : Optional.of(settleOnlinePlayer(context, player));
        }
    }
}
