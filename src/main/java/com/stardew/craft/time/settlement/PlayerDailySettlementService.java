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
import java.util.function.Consumer;
import java.util.function.Function;

public final class PlayerDailySettlementService {
    private static final int BASE_STAGE = 1;
    private static final int SHIPPING_STAGE = 2;
    private static final int LEVELS_STAGE = 3;
    private static final int SYNC_STAGE = 4;
    private static final int QUEST_STAGE = 5;
    private static final int MASTERY_STAGE = 6;

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
        pending.save(context, playerId);
        Optional<OvernightSettlementPayload> payload = settleIfOnline(context, playerId);
        if (payload.isEmpty()) {
            readyResults.putIfAbsent(playerId, pendingReadyResult(context));
            return;
        }
        pending.clear(playerId, context.absoluteDay());
        readyResults.put(playerId,
                new DailySettlementBarrier.ReadyResult(context.absoluteDay(), payload.orElseThrow()));
    }

    private Optional<OvernightSettlementPayload> settleIfOnline(
            DailySettlementContext context, UUID playerId) {
        @SuppressWarnings("unchecked")
        Optional<OvernightSettlementPayload>[] result = new Optional[] {Optional.empty()};
        PendingSettlement progress = pending.find(playerId)
                .orElseThrow(() -> new IllegalStateException(
                        "Missing pending settlement for " + playerId));
        try {
            DailySettlementDateView.run(
                    context, () -> result[0] = backend.settleIfOnline(
                            context, playerId, progress,
                            next -> pending.checkpoint(playerId, next)));
        } catch (RuntimeException | Error failure) {
            throw failure;
        } catch (Exception failure) {
            throw new IllegalStateException("Unable to settle player " + playerId, failure);
        }
        return result[0];
    }

    private static OvernightSettlementPayload settleOnlinePlayer(
            DailySettlementContext context,
            ServerPlayer player,
            PendingSettlement progress,
            Consumer<PendingSettlement> checkpoint) {
        if (progress.stage() < BASE_STAGE) {
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
            checkpoint.accept(progress.atStage(BASE_STAGE));
        }

        OvernightSettlementPayload settlementPayload = OvernightSettlementTracker.peekPayload(
                player.server, player.getUUID(), context.absoluteDay());
        if (progress.stage() < SHIPPING_STAGE) {
            PlayerStardewDataAPI.recordOvernightShippedItems(player, settlementPayload.shippedItems());
            checkpoint.accept(progress.atStage(SHIPPING_STAGE));
        }

        List<PlayerStardewData.SkillLevelUp> appliedLevelUps = progress.appliedLevels();
        if (progress.stage() < LEVELS_STAGE) {
            appliedLevelUps = PlayerStardewDataAPI.applyPendingSkillLevelUpsForSettlement(player);
            PlayerStardewDataAPI.applySkillLevelRecipeUnlocks(player, appliedLevelUps);
            if (!appliedLevelUps.isEmpty()) {
                PlayerStardewDataAPI.restoreEnergy(player, PlayerStardewDataAPI.getMaxEnergy(player));
                PlayerStardewDataAPI.setHealth(player, PlayerStardewDataAPI.getMaxHealth(player));
            }
            checkpoint.accept(progress.atStage(LEVELS_STAGE, appliedLevelUps));
        }

        if (progress.stage() < SYNC_STAGE) {
            com.stardew.craft.player.PlayerDataEventHandler.syncPlayerData(
                    player, com.stardew.craft.player.PlayerDataManager.getPlayerData(player));
            checkpoint.accept(progress.atStage(SYNC_STAGE, appliedLevelUps));
        }
        if (progress.stage() < QUEST_STAGE) {
            com.stardew.craft.quest.StardewQuestEvents.fireDayStarted(player, context.absoluteDay());
            checkpoint.accept(progress.atStage(QUEST_STAGE, appliedLevelUps));
        }
        if (progress.stage() < MASTERY_STAGE) {
            com.stardew.craft.mastery.MasteryOnboardingService.checkOnMorning(player);
            checkpoint.accept(progress.atStage(MASTERY_STAGE, appliedLevelUps));
        }

        OvernightSettlementPayload result = buildPayload(
                context, player.getUUID(), settlementPayload, appliedLevelUps);
        OvernightSettlementTracker.consumePayload(
                player.server, player.getUUID(), context.absoluteDay());
        PlayerStardewDataAPI.clearPendingSkillLevelUps(player);
        return result;
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

        default Optional<OvernightSettlementPayload> settleIfOnline(
                DailySettlementContext context,
                UUID playerId,
                PendingSettlement progress,
                Consumer<PendingSettlement> checkpoint) {
            return settleIfOnline(context, playerId);
        }
    }

    interface PendingStore {
        Optional<PendingSettlement> find(UUID playerId);

        void save(DailySettlementContext context, UUID playerId);

        void clear(UUID playerId, int absoluteDay);

        void checkpoint(UUID playerId, PendingSettlement progress);
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
            Optional<PlayerStardewData.PendingDailySettlement> existing =
                    data(playerId).getPendingDailySettlement();
            if (existing.isPresent()) {
                PlayerStardewData.PendingDailySettlement scheduled = existing.orElseThrow();
                if (scheduled.absoluteDay() == context.absoluteDay()
                        && scheduled.year() == context.year()
                        && scheduled.season() == context.season()
                        && scheduled.day() == context.day()
                        && scheduled.sleepMinute() == context.sleepMinute()
                        && scheduled.seasonChanged() == context.seasonChanged()) {
                    return;
                }
            }
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

        @Override
        public void checkpoint(UUID playerId, PendingSettlement progress) {
            PlayerStardewData.PendingDailySettlement scheduled =
                    new PlayerStardewData.PendingDailySettlement(
                            progress.absoluteDay(), progress.year(), progress.season(), progress.day(),
                            progress.sleepMinute(), progress.seasonChanged(), progress.stage(),
                            progress.appliedLevels());
            if (data(playerId).updatePendingDailySettlement(scheduled)) {
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
            boolean seasonChanged,
            int stage,
            List<PlayerStardewData.SkillLevelUp> appliedLevels) {

        PendingSettlement(
                int absoluteDay,
                int year,
                int season,
                int day,
                int sleepMinute,
                boolean seasonChanged) {
            this(absoluteDay, year, season, day, sleepMinute, seasonChanged, 0, List.of());
        }

        private static PendingSettlement fromData(
                PlayerStardewData.PendingDailySettlement pending) {
            return new PendingSettlement(
                    pending.absoluteDay(), pending.year(), pending.season(), pending.day(),
                    pending.sleepMinute(), pending.seasonChanged(), pending.stage(),
                    pending.appliedLevels());
        }

        private DailySettlementContext context(UUID playerId) {
            return new DailySettlementContext(
                    absoluteDay, year, season, day, sleepMinute, seasonChanged,
                    List.of(playerId), Set.of());
        }

        PendingSettlement atStage(int nextStage) {
            return atStage(nextStage, appliedLevels);
        }

        PendingSettlement atStage(
                int nextStage, List<PlayerStardewData.SkillLevelUp> nextLevels) {
            return new PendingSettlement(
                    absoluteDay, year, season, day, sleepMinute, seasonChanged,
                    nextStage, List.copyOf(nextLevels));
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
                    : Optional.of(settleOnlinePlayer(
                            context,
                            player,
                            new PendingSettlement(
                                    context.absoluteDay(), context.year(), context.season(),
                                    context.day(), context.sleepMinute(), context.seasonChanged()),
                            ignored -> {}));
        }

        @Override
        public Optional<OvernightSettlementPayload> settleIfOnline(
                DailySettlementContext context,
                UUID playerId,
                PendingSettlement progress,
                Consumer<PendingSettlement> checkpoint) {
            MinecraftServer current = server.get();
            if (current == null) {
                throw new IllegalStateException("Daily settlement server is no longer available");
            }
            ServerPlayer player = current.getPlayerList().getPlayer(playerId);
            return player == null
                    ? Optional.empty()
                    : Optional.of(settleOnlinePlayer(context, player, progress, checkpoint));
        }
    }
}
