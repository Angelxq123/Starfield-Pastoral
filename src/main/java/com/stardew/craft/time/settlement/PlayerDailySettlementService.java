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
    private static final int SHIPPING_HISTORY_STAGE = 2;
    private static final int SHIPPING_ORDERS_STAGE = 3;
    private static final int SHIPPING_MONEY_STAGE = 4;
    private static final int LEVELS_STAGE = 5;
    private static final int RECIPES_STAGE = 6;
    private static final int SYNC_STAGE = 7;
    private static final int QUEST_STAGE = 8;
    private static final int MASTERY_STAGE = 9;
    private static final int SHIPPING_CLEANUP_STAGE = 10;
    private static final int PASS_OUT_CLEANUP_STAGE = 11;
    private static final int LEVEL_CLEANUP_STAGE = 12;

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
        return isInSettlementDimension(player)
                && com.stardew.craft.farm.FarmInstanceRegistry.get().hasFarm(player.getUUID());
    }

    public boolean requiresNonParticipantCleanup(ServerPlayer player) {
        Objects.requireNonNull(player, "player");
        return isInSettlementDimension(player)
                && !com.stardew.craft.farm.FarmInstanceRegistry.get().hasFarm(player.getUUID());
    }

    private static boolean isInSettlementDimension(ServerPlayer player) {
        return player.level().dimension() == ModDimensions.STARDEW_VALLEY
                || player.level().dimension() == ModMiningDimensions.STARDEW_MINING;
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
        Optional<PendingSettlement> progress = pending.find(playerId);
        DailySettlementBarrier.ReadyResult completed = readyResult(
                playerId, context.absoluteDay());
        if (completed != null && (progress.isEmpty()
                || progress.orElseThrow().stage() >= MASTERY_STAGE)) {
            finishPreparedSettlement(context, playerId);
            return;
        }
        pending.save(context, playerId);
        Optional<OvernightSettlementPayload> payload = settleIfOnline(context, playerId);
        if (payload.isEmpty()) {
            return;
        }
        DailySettlementBarrier.ReadyResult prepared = new DailySettlementBarrier.ReadyResult(
                context.absoluteDay(), payload.orElseThrow());
        pending.complete(playerId, prepared.payload());
        readyResults.put(playerId, prepared);
        finishPreparedSettlement(context, playerId);
    }

    private void finishPreparedSettlement(DailySettlementContext context, UUID playerId) {
        Optional<PendingSettlement> progress = pending.find(playerId);
        if (progress.isEmpty()) {
            return;
        }
        backend.finalizeSettlement(
                context, playerId, progress.orElseThrow(),
                next -> pending.checkpoint(playerId, next));
        PendingSettlement finalized = pending.find(playerId).orElse(progress.orElseThrow());
        if (finalized.stage() < LEVEL_CLEANUP_STAGE) {
            pending.checkpoint(playerId, finalized.atStage(LEVEL_CLEANUP_STAGE));
        }
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
        if (result[0].isPresent()) {
            PendingSettlement completed = pending.find(playerId).orElse(progress);
            if (completed.stage() < MASTERY_STAGE) {
                pending.checkpoint(playerId, completed.atStage(MASTERY_STAGE));
            }
        }
        return result[0];
    }

    private static OvernightSettlementPayload settleOnlinePlayer(
            DailySettlementContext context,
            ServerPlayer player,
            PendingSettlement progress,
            Consumer<PendingSettlement> checkpoint) {
        return prepareOnlineSettlement(
                context, player.getUUID(), progress, checkpoint,
                new ProductionSettlementOperations(player.server, player.getUUID(), player));
    }

    static OvernightSettlementPayload prepareOnlineSettlement(
            DailySettlementContext context,
            UUID playerId,
            PendingSettlement progress,
            Consumer<PendingSettlement> checkpoint,
            SettlementOperations operations) {
        if (progress.stage() < BASE_STAGE) {
            operations.applyBase(context);
            checkpoint.accept(progress.atStage(BASE_STAGE));
        }

        OvernightSettlementPayload settlementPayload = operations.shippingPayload(context.absoluteDay());
        if (progress.stage() < SHIPPING_HISTORY_STAGE) {
            operations.applyShippingHistory(context.absoluteDay(), settlementPayload);
            checkpoint.accept(progress.atStage(SHIPPING_HISTORY_STAGE));
        }
        if (progress.stage() < SHIPPING_ORDERS_STAGE) {
            operations.applyShippingOrders(context.absoluteDay(), settlementPayload);
            checkpoint.accept(progress.atStage(SHIPPING_ORDERS_STAGE));
        }
        if (progress.stage() < SHIPPING_MONEY_STAGE) {
            operations.applyShippingMoney(context.absoluteDay(), settlementPayload);
            checkpoint.accept(progress.atStage(SHIPPING_MONEY_STAGE));
        }

        List<PlayerStardewData.SkillLevelUp> appliedLevelUps = progress.appliedLevels();
        if (progress.stage() < LEVELS_STAGE) {
            appliedLevelUps = operations.applyLevels();
            checkpoint.accept(progress.atStage(LEVELS_STAGE, appliedLevelUps));
        }
        if (progress.stage() < RECIPES_STAGE) {
            operations.applyRecipes(appliedLevelUps);
            checkpoint.accept(progress.atStage(RECIPES_STAGE, appliedLevelUps));
        }

        if (progress.stage() < SYNC_STAGE) {
            operations.syncPlayer();
            checkpoint.accept(progress.atStage(SYNC_STAGE, appliedLevelUps));
        }
        if (progress.stage() < QUEST_STAGE) {
            operations.applyQuest(context.absoluteDay());
            checkpoint.accept(progress.atStage(QUEST_STAGE, appliedLevelUps));
        }
        if (progress.stage() < MASTERY_STAGE) {
            operations.applyMastery(context.absoluteDay());
            checkpoint.accept(progress.atStage(MASTERY_STAGE, appliedLevelUps));
        }

        return buildPayload(
                context, playerId, settlementPayload, appliedLevelUps,
                operations.passOutResult(context.absoluteDay()));
    }

    static void finalizeOnlineSettlement(
            DailySettlementContext context,
            UUID playerId,
            PendingSettlement progress,
            Consumer<PendingSettlement> checkpoint,
            SettlementOperations operations) {
        if (progress.stage() < SHIPPING_CLEANUP_STAGE) {
            operations.consumeShipping(context.absoluteDay());
            checkpoint.accept(progress.atStage(SHIPPING_CLEANUP_STAGE));
        }
        if (progress.stage() < PASS_OUT_CLEANUP_STAGE) {
            operations.consumePassOut(context.absoluteDay());
            checkpoint.accept(progress.atStage(PASS_OUT_CLEANUP_STAGE));
        }
        if (progress.stage() < LEVEL_CLEANUP_STAGE) {
            operations.clearLevels();
            checkpoint.accept(progress.atStage(LEVEL_CLEANUP_STAGE));
        }
    }

    private static OvernightSettlementPayload buildPayload(
            DailySettlementContext context,
            UUID playerId,
            OvernightSettlementPayload settlementPayload,
            List<PlayerStardewData.SkillLevelUp> appliedLevelUps,
            PassOutService.PassOutResult passOutResult) {
        List<OvernightSettlementPayload.LevelUpData> levelUps =
                new ArrayList<>(settlementPayload.levelUps());
        for (PlayerStardewData.SkillLevelUp levelUp : appliedLevelUps) {
            levelUps.add(new OvernightSettlementPayload.LevelUpData(
                    levelUp.skill().getId(), levelUp.newLevel()));
        }

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
        if (result != null && result.absoluteDay() == absoluteDay) {
            return result;
        }
        DailySettlementBarrier.ReadyResult persisted = pending.find(playerId)
                .filter(progress -> progress.absoluteDay() == absoluteDay)
                .flatMap(PendingSettlement::completedPayload)
                .map(payload -> new DailySettlementBarrier.ReadyResult(absoluteDay, payload))
                .orElse(null);
        if (persisted != null) {
            readyResults.put(playerId, persisted);
        }
        return persisted;
    }

    DailySettlementBarrier.ReadyResult readyResultOrCreate(
            DailySettlementContext context, UUID playerId) {
        DailySettlementBarrier.ReadyResult result = readyResult(
                playerId, context.absoluteDay());
        if (result != null) {
            Optional<PendingSettlement> progress = pending.find(playerId);
            if (progress.isEmpty()) {
                return result;
            }
            if (progress.orElseThrow().completedPayload().isPresent()) {
                finishPreparedSettlement(context, playerId);
                return result;
            }
        }
        pending.save(context, playerId);
        Optional<OvernightSettlementPayload> payload = settleIfOnline(context, playerId);
        if (payload.isPresent()) {
            DailySettlementBarrier.ReadyResult completed =
                    new DailySettlementBarrier.ReadyResult(
                            context.absoluteDay(), payload.orElseThrow());
            pending.complete(playerId, completed.payload());
            readyResults.put(playerId, completed);
            finishPreparedSettlement(context, playerId);
            return completed;
        }
        DailySettlementBarrier.ReadyResult fallback = pendingReadyResult(context);
        readyResults.put(playerId, fallback);
        return fallback;
    }

    public boolean hasCompletedReady(UUID playerId, int absoluteDay) {
        Objects.requireNonNull(playerId, "playerId");
        return pending.find(playerId)
                .filter(progress -> progress.absoluteDay() == absoluteDay)
                .filter(progress -> progress.stage() >= LEVEL_CLEANUP_STAGE)
                .flatMap(PendingSettlement::completedPayload)
                .isPresent();
    }

    Optional<DailySettlementBarrier.ReadyResult> onLogin(
            UUID playerId, DailySettlementBarrier barrier) {
        Objects.requireNonNull(playerId, "playerId");
        Optional<PendingSettlement> scheduled = pending.find(playerId);
        if (scheduled.isEmpty()) {
            return Optional.empty();
        }
        DailySettlementContext context = scheduled.orElseThrow().context(playerId);
        DailySettlementBarrier.ReadyResult result = readyResult(
                playerId, context.absoluteDay());
        if (result == null || scheduled.orElseThrow().stage() < MASTERY_STAGE) {
            Optional<OvernightSettlementPayload> payload = settleIfOnline(context, playerId);
            if (payload.isEmpty()) {
                return Optional.empty();
            }
            DailySettlementBarrier.ReadyResult prepared = new DailySettlementBarrier.ReadyResult(
                    context.absoluteDay(), payload.orElseThrow());
            pending.complete(playerId, prepared.payload());
            readyResults.put(playerId, prepared);
            result = prepared;
        }
        finishPreparedSettlement(context, playerId);
        if (barrier != null) {
            boolean restoredLock = barrier.lockedDay(playerId) <= 0;
            if (restoredLock) {
                barrier.lockAll(context.absoluteDay(), List.of(playerId));
            }
            DailySettlementBarrier.ReadyResult retained =
                    barrier.readyResult(playerId, context.absoluteDay());
            boolean published = retained != null
                    ? barrier.replaceReady(playerId, result)
                    : !restoredLock || barrier.publishReady(playerId, result);
            if (!published) {
                throw new IllegalStateException(
                        "Unable to retain settlement result for " + playerId);
            }
        }
        return Optional.of(result);
    }

    public boolean acknowledgeReady(UUID playerId, int absoluteDay) {
        Objects.requireNonNull(playerId, "playerId");
        if (!pending.acknowledge(playerId, absoluteDay)) {
            return false;
        }
        readyResults.computeIfPresent(playerId,
                (ignored, ready) -> ready.absoluteDay() == absoluteDay ? null : ready);
        return true;
    }

    public static boolean acknowledgeReady(ServerPlayer player, int absoluteDay) {
        Objects.requireNonNull(player, "player");
        return new PlayerDailySettlementService(player.server)
                .acknowledgeReady(player.getUUID(), absoluteDay);
    }

    public static boolean hasUnacknowledgedReady(ServerPlayer player) {
        Objects.requireNonNull(player, "player");
        return com.stardew.craft.player.PlayerDataManager.getPlayerData(player.getUUID())
                .hasUnacknowledgedDailySettlementReady();
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

        default void finalizeSettlement(
                DailySettlementContext context,
                UUID playerId,
                PendingSettlement progress,
                Consumer<PendingSettlement> checkpoint) {
        }
    }

    interface SettlementOperations {
        void applyBase(DailySettlementContext context);

        OvernightSettlementPayload shippingPayload(int absoluteDay);

        void applyShippingHistory(int absoluteDay, OvernightSettlementPayload payload);

        void applyShippingOrders(int absoluteDay, OvernightSettlementPayload payload);

        void applyShippingMoney(int absoluteDay, OvernightSettlementPayload payload);

        List<PlayerStardewData.SkillLevelUp> applyLevels();

        void applyRecipes(List<PlayerStardewData.SkillLevelUp> levels);

        void syncPlayer();

        void applyQuest(int absoluteDay);

        void applyMastery(int absoluteDay);

        PassOutService.PassOutResult passOutResult(int absoluteDay);

        void consumeShipping(int absoluteDay);

        void consumePassOut(int absoluteDay);

        void clearLevels();
    }

    interface PendingStore {
        Optional<PendingSettlement> find(UUID playerId);

        void save(DailySettlementContext context, UUID playerId);

        void clear(UUID playerId, int absoluteDay);

        void checkpoint(UUID playerId, PendingSettlement progress);

        void complete(UUID playerId, OvernightSettlementPayload payload);

        boolean acknowledge(UUID playerId, int absoluteDay);
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
                            progress.appliedLevels(), progress.completedPayload());
            if (data(playerId).updatePendingDailySettlement(scheduled)) {
                markPersistent.run();
            }
        }

        @Override
        public void complete(UUID playerId, OvernightSettlementPayload payload) {
            PlayerStardewData.PendingDailySettlement existing = data(playerId)
                    .getPendingDailySettlement()
                    .orElseThrow(() -> new IllegalStateException(
                            "Missing pending settlement for " + playerId));
            PlayerStardewData.PendingDailySettlement completed =
                    new PlayerStardewData.PendingDailySettlement(
                            existing.absoluteDay(), existing.year(), existing.season(),
                            existing.day(), existing.sleepMinute(), existing.seasonChanged(),
                            existing.stage(), existing.appliedLevels(), Optional.of(payload));
            if (data(playerId).updatePendingDailySettlement(completed)) {
                markPersistent.run();
            }
        }

        @Override
        public boolean acknowledge(UUID playerId, int absoluteDay) {
            if (!data(playerId).acknowledgePendingDailySettlement(absoluteDay)) {
                return false;
            }
            markPersistent.run();
            return true;
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
            List<PlayerStardewData.SkillLevelUp> appliedLevels,
            Optional<OvernightSettlementPayload> completedPayload) {

        PendingSettlement(
                int absoluteDay,
                int year,
                int season,
                int day,
                int sleepMinute,
                boolean seasonChanged) {
            this(absoluteDay, year, season, day, sleepMinute, seasonChanged,
                    0, List.of(), Optional.empty());
        }

        private static PendingSettlement fromData(
                PlayerStardewData.PendingDailySettlement pending) {
            return new PendingSettlement(
                    pending.absoluteDay(), pending.year(), pending.season(), pending.day(),
                    pending.sleepMinute(), pending.seasonChanged(), pending.stage(),
                    pending.appliedLevels(), pending.completedPayload());
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
                    nextStage, List.copyOf(nextLevels), completedPayload);
        }

        PendingSettlement {
            appliedLevels = List.copyOf(Objects.requireNonNull(appliedLevels, "appliedLevels"));
            completedPayload = Objects.requireNonNull(completedPayload, "completedPayload");
        }
    }

    private static final class ProductionSettlementOperations implements SettlementOperations {
        private final WeakReference<MinecraftServer> server;
        private final UUID playerId;
        private final ServerPlayer player;

        private ProductionSettlementOperations(
                MinecraftServer server, UUID playerId, ServerPlayer player) {
            this.server = new WeakReference<>(Objects.requireNonNull(server, "server"));
            this.playerId = Objects.requireNonNull(playerId, "playerId");
            this.player = player;
        }

        @Override
        public void applyBase(DailySettlementContext context) {
            ServerPlayer current = requirePlayer();
            if (current.isCreative()) {
                PlayerStardewDataAPI.cureExhaustion(current);
                PlayerStardewDataAPI.restoreEnergy(
                        current, PlayerStardewDataAPI.getMaxEnergy(current));
                com.stardew.craft.mastery.MasteryBuffLifecycle.clearAllDailyMasteryBuffs(current);
            } else {
                PlayerStardewDataAPI.sleep(current, context.sleepMinute());
                PassOutService.applyCombatDeathEnergyPenalty(current);
            }
            PlayerStardewDataAPI.setHealth(current, PlayerStardewDataAPI.getMaxHealth(current));
            com.stardew.craft.shop.BlacksmithService.onNewDay(current);
            com.stardew.craft.shop.BlacksmithService.showToolUpgradeNotification(current);
        }

        @Override
        public OvernightSettlementPayload shippingPayload(int absoluteDay) {
            return OvernightSettlementTracker.peekPayload(server(), playerId, absoluteDay);
        }

        @Override
        public void applyShippingHistory(int absoluteDay, OvernightSettlementPayload payload) {
            PlayerStardewDataAPI.applyOvernightShippingHistory(
                    requirePlayer(), absoluteDay, payload.shippedItems());
        }

        @Override
        public void applyShippingOrders(int absoluteDay, OvernightSettlementPayload payload) {
            PlayerStardewDataAPI.applyOvernightShippingOrders(
                    requirePlayer(), absoluteDay, payload.shippedItems());
        }

        @Override
        public void applyShippingMoney(int absoluteDay, OvernightSettlementPayload payload) {
            PlayerStardewDataAPI.applyOvernightShippingMoney(
                    requirePlayer(), absoluteDay, payload.shippedItems());
        }

        @Override
        public List<PlayerStardewData.SkillLevelUp> applyLevels() {
            return PlayerStardewDataAPI.applyPendingSkillLevelUpsForSettlement(requirePlayer());
        }

        @Override
        public void applyRecipes(List<PlayerStardewData.SkillLevelUp> levels) {
            ServerPlayer current = requirePlayer();
            PlayerStardewDataAPI.applySkillLevelRecipeUnlocks(current, levels);
            if (!levels.isEmpty()) {
                PlayerStardewDataAPI.restoreEnergy(
                        current, PlayerStardewDataAPI.getMaxEnergy(current));
                PlayerStardewDataAPI.setHealth(current, PlayerStardewDataAPI.getMaxHealth(current));
            }
        }

        @Override
        public void syncPlayer() {
            PlayerStardewDataAPI.syncOvernightSettlement(requirePlayer());
        }

        @Override
        public void applyQuest(int absoluteDay) {
            ServerPlayer current = requirePlayer();
            PlayerStardewData data = com.stardew.craft.player.PlayerDataManager
                    .getPlayerData(current);
            if (!data.markDailySettlementQuestApplied(absoluteDay)) {
                return;
            }
            com.stardew.craft.quest.StardewQuestEvents.fireDayStarted(current, absoluteDay);
        }

        @Override
        public void applyMastery(int absoluteDay) {
            ServerPlayer current = requirePlayer();
            PlayerStardewData data = com.stardew.craft.player.PlayerDataManager
                    .getPlayerData(current);
            if (!data.markDailySettlementMasteryApplied(absoluteDay)) {
                return;
            }
            com.stardew.craft.mastery.MasteryOnboardingService.checkOnMorning(current);
        }

        @Override
        public PassOutService.PassOutResult passOutResult(int absoluteDay) {
            return PassOutService.peekPassOutResult(playerId, absoluteDay);
        }

        @Override
        public void consumeShipping(int absoluteDay) {
            OvernightSettlementTracker.consumePayload(server(), playerId, absoluteDay);
        }

        @Override
        public void consumePassOut(int absoluteDay) {
            PassOutService.consumePassOutResult(playerId, absoluteDay);
        }

        @Override
        public void clearLevels() {
            com.stardew.craft.player.PlayerDataManager.getPlayerData(playerId)
                    .clearPendingSkillLevelUps();
        }

        private ServerPlayer requirePlayer() {
            if (player == null) {
                throw new IllegalStateException("Player " + playerId + " is not online");
            }
            return player;
        }

        private MinecraftServer server() {
            MinecraftServer current = server.get();
            if (current == null) {
                throw new IllegalStateException("Daily settlement server is no longer available");
            }
            return current;
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

        @Override
        public void finalizeSettlement(
                DailySettlementContext context,
                UUID playerId,
                PendingSettlement progress,
                Consumer<PendingSettlement> checkpoint) {
            MinecraftServer current = server.get();
            if (current == null) {
                throw new IllegalStateException("Daily settlement server is no longer available");
            }
            finalizeOnlineSettlement(
                    context, playerId, progress, checkpoint,
                    new ProductionSettlementOperations(
                            current, playerId, current.getPlayerList().getPlayer(playerId)));
        }
    }
}
