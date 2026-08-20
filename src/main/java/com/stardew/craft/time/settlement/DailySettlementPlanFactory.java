package com.stardew.craft.time.settlement;

import com.stardew.craft.server.performance.DailySettlementMetrics;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.lang.ref.WeakReference;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

public final class DailySettlementPlanFactory implements DailySettlementCoordinator.PlanFactory {
    private static final List<String> PREPARE = List.of(
            "shipping_bin_flush",
            "non_participant_cleanup",
            "daily_process_scope");
    private static final List<String> WORLD = List.of(
            "festival_season_prep",
            "npc_friendship_daily",
            "npc_dialogue_events",
            "npc_dialogue_topics",
            "weather_npc_reset",
            "crops",
            "trees",
            "fruit_trees",
            "tea_bushes",
            "wild_tree_seeds",
            "farm_debris",
            "sprinklers",
            "pasture_grass",
            "animals",
            "fish_ponds",
            "public_forage",
            "forest_farm_forage",
            "artifact_spots",
            "quarry",
            "coal_forest",
            "secret_woods_entrance",
            "farm_caves",
            "addon_farm_tasks");
    private static final List<String> PLAYERS = List.of("player_daily_settlement");
    private static final List<String> WORLD_SNAPSHOTS = List.of(
            "crops", "trees", "fruit_trees", "tea_bushes", "wild_tree_seeds", "farm_debris",
            "sprinklers", "pasture_grass", "animals", "fish_ponds",
            "public_forage", "forest_farm_forage", "artifact_spots",
            "quarry", "coal_forest", "farm_caves", "addon_farm_tasks");
    private static final List<String> COMMIT = List.of(
            "player_daily_cleanup",
            "weather_forecast",
            "farm_cursor",
            "special_orders",
            "lost_and_found",
            "bookseller",
            "shop_stock",
            "mail",
            "dirty_mark",
            "daily_process_cleanup",
            "date_publication");

    private final WorkUnitFactory workUnits;

    public DailySettlementPlanFactory(WorkUnitFactory workUnits) {
        this.workUnits = Objects.requireNonNull(workUnits, "workUnits");
    }

    public DailySettlementPlanFactory(
            MinecraftServer server,
            DailySettlementBarrier barrier,
            PlayerDailySettlementService players) {
        this(server, barrier, players, DailySettlementCommitHooks.production(server));
    }

    DailySettlementPlanFactory(
            MinecraftServer server,
            DailySettlementBarrier barrier,
            PlayerDailySettlementService players,
            DailySettlementCommitHooks commitHooks) {
        this(server, barrier, players, commitHooks,
                new DailySettlementAccessGuard(barrier));
    }

    DailySettlementPlanFactory(
            MinecraftServer server,
            DailySettlementBarrier barrier,
            PlayerDailySettlementService players,
            DailySettlementCommitHooks commitHooks,
            DailySettlementAccessGuard accessGuard) {
        this(server, barrier, players, commitHooks, accessGuard, null);
    }

    DailySettlementPlanFactory(
            MinecraftServer server,
            DailySettlementBarrier barrier,
            PlayerDailySettlementService players,
            DailySettlementCommitHooks commitHooks,
            DailySettlementAccessGuard accessGuard,
            DailySettlementMetrics metrics) {
        this(new ProductionWorkUnitFactory(
                server, barrier, players, commitHooks, accessGuard, metrics));
    }

    @Override
    public void build(
            DailySettlementContext context,
            DailySettlementCoordinator.SettlementPlanBuilder builder) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(builder, "builder");
        try {
            PREPARE.forEach(name -> builder.addPrepare(create(name, context)));
            WORLD.forEach(name -> builder.addWorld(create(name, context)));
            PLAYERS.forEach(name -> builder.addPlayer(create(name, context)));
            COMMIT.forEach(name -> builder.addCommit(create(name, context)));
        } catch (RuntimeException | Error failure) {
            try {
                workUnits.cleanup();
            } catch (RuntimeException | Error cleanupFailure) {
                if (cleanupFailure != failure) {
                    failure.addSuppressed(cleanupFailure);
                }
            }
            throw failure;
        }
    }

    @Override
    public void prepareStart(DailySettlementContext context) {
        Objects.requireNonNull(context, "context");
        workUnits.prepareStart(context);
    }

    private DailySettlementWorkUnit create(String name, DailySettlementContext context) {
        return Objects.requireNonNull(workUnits.create(name, context), "work unit " + name);
    }

    public void cleanup() {
        workUnits.cleanup();
    }

    static Map<UUID, com.stardew.craft.farm.FarmInstance> snapshotFarms(
            DailySettlementContext context,
            com.stardew.craft.farm.FarmInstanceRegistry registry) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(registry, "registry");
        Map<UUID, com.stardew.craft.farm.FarmInstance> snapshot = new LinkedHashMap<>();
        context.farmOwnerIds().stream()
                .sorted(java.util.Comparator.comparing(UUID::toString))
                .forEach(ownerId -> {
                    com.stardew.craft.farm.FarmInstance farm = registry.getFarm(ownerId);
                    if (farm != null) {
                        snapshot.put(ownerId, farm);
                    }
                });
        return Map.copyOf(snapshot);
    }

    static void lockBarrierAtStart(
            DailySettlementContext context,
            DailySettlementBarrier barrier,
            DailySettlementAccessGuard accessGuard,
            DailySettlementMetrics metrics,
            BarrierNotifier notifier) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(barrier, "barrier");
        Objects.requireNonNull(accessGuard, "accessGuard");
        Objects.requireNonNull(notifier, "notifier");
        List<UUID> notifiedPlayers = new ArrayList<>();
        DailySettlementBarrier.LockScope scope = null;
        try {
            scope = barrier.lockAllScoped(context.absoluteDay(), context.playerIds());
            if (metrics != null) {
                metrics.markLocked();
            }
            for (UUID playerId : context.playerIds()) {
                if (notifier.send(playerId, context.absoluteDay(), true)) {
                    notifiedPlayers.add(playerId);
                }
            }
            return;
        } catch (RuntimeException | Error failure) {
            for (UUID playerId : notifiedPlayers) {
                try {
                    notifier.send(playerId, context.absoluteDay(), false);
                } catch (RuntimeException | Error unlockFailure) {
                    if (unlockFailure != failure) {
                        failure.addSuppressed(unlockFailure);
                    }
                }
            }
            if (scope != null) {
                accessGuard.clearAnchors(scope.newlyLocked());
                barrier.rollback(scope);
            }
            throw failure;
        }
    }

    @FunctionalInterface
    interface BarrierNotifier {
        boolean send(UUID playerId, int absoluteDay, boolean locked);
    }

    @FunctionalInterface
    public interface WorkUnitFactory {
        DailySettlementWorkUnit create(String name, DailySettlementContext context);

        default void prepareStart(DailySettlementContext context) {
        }

        default void cleanup() {
        }
    }

    private static final class ProductionWorkUnitFactory implements WorkUnitFactory {
        private final WeakReference<MinecraftServer> server;
        private final DailySettlementBarrier barrier;
        private final PlayerDailySettlementService players;
        private final DailySettlementCommitHooks commitHooks;
        private final DailySettlementAccessGuard accessGuard;
        private final DailySettlementMetrics metrics;
        private final Map<String, DailySettlementWorkUnit> preparedWorld =
                new LinkedHashMap<>();
        private Map<UUID, com.stardew.craft.farm.FarmInstance> frozenFarms = Map.of();
        private int frozenFarmDay = Integer.MIN_VALUE;
        private ServerLevel activeLevel;
        private boolean dailyProcessActive;

        private ProductionWorkUnitFactory(
                MinecraftServer server,
                DailySettlementBarrier barrier,
                PlayerDailySettlementService players,
                DailySettlementCommitHooks commitHooks,
                DailySettlementAccessGuard accessGuard,
                DailySettlementMetrics metrics) {
            this.server = new WeakReference<>(Objects.requireNonNull(server, "server"));
            this.barrier = Objects.requireNonNull(barrier, "barrier");
            this.players = Objects.requireNonNull(players, "players");
            this.commitHooks = Objects.requireNonNull(commitHooks, "commitHooks");
            this.accessGuard = Objects.requireNonNull(accessGuard, "accessGuard");
            this.metrics = metrics;
        }

        @Override
        public DailySettlementWorkUnit create(String name, DailySettlementContext context) {
            freezeFarms(context);
            return switch (name) {
                case "shipping_bin_flush" -> atomic(name,
                        com.stardew.craft.blockentity.ShippingBinBlockEntity::flushAllForOvernight);
                case "non_participant_cleanup" -> createNonParticipantCleanupWorkUnit(
                        context, playerId -> cleanupNonParticipant(context, playerId));
                case "daily_process_scope" -> createDailyProcessScope(context);
                case "festival_season_prep" -> atomic(name, () -> festivalAndSeason(context));
                case "npc_friendship_daily" ->
                        com.stardew.craft.npc.runtime.NpcFriendshipDailyService
                                .createDailyWorkUnit(
                                        level(),
                                        context.absoluteDay() - 1,
                                        context.absoluteDay());
                case "npc_dialogue_events" -> createDialogueEventsDaily();
                case "npc_dialogue_topics" -> atomic(name, () -> dialogueTopicsDaily(context));
                case "weather_npc_reset" -> createWeatherAndNpcWorkUnit(context);
                case "crops", "trees", "fruit_trees", "tea_bushes", "wild_tree_seeds", "farm_debris",
                        "sprinklers", "pasture_grass", "animals", "fish_ponds",
                        "public_forage", "forest_farm_forage", "artifact_spots",
                        "quarry", "coal_forest", "farm_caves", "addon_farm_tasks" -> prepared(name);
                case "secret_woods_entrance" -> atomic(name, () ->
                        com.stardew.craft.manager.SecretWoodsAccessManager.ensureEntranceReady(level()));
                case "player_daily_settlement" -> players.createDailyWorkUnit(context);
                case "player_daily_cleanup" -> players.createFinalizationWorkUnit(context);
                case "weather_forecast" -> atomic(name, () -> forecast(context));
                case "farm_cursor" -> atomic(name, () -> updateFarmCursor(context));
                case "special_orders", "bookseller", "mail" ->
                        commitHooks.create(name, context);
                case "lost_and_found" -> atomic(name, () ->
                        com.stardew.craft.lostandfound.LostAndFoundService.onNewDay(level()));
                case "shop_stock" -> atomic(name,
                        com.stardew.craft.shop.ShopStockTracker::resetForNewDay);
                case "dirty_mark" -> atomic(name, () ->
                        com.stardew.craft.farm.FarmInstanceRegistry.get().setDirty());
                case "daily_process_cleanup" -> requiredAtomic(name, this::cleanupDailyProcess);
                case "date_publication" -> publication(context);
                default -> throw new IllegalArgumentException("Unknown settlement work unit: " + name);
            };
        }

        @Override
        public void prepareStart(DailySettlementContext context) {
            lockBarrier(context);
        }

        @Override
        public void cleanup() {
            cleanupDailyProcess();
        }

        private DailySettlementWorkUnit atomic(
                String name, DailySettlementWorkUnits.ThrowingRunnable action) {
            return DailySettlementWorkUnits.atomic(name, action, () -> {});
        }

        private DailySettlementWorkUnit requiredAtomic(
                String name, DailySettlementWorkUnits.ThrowingRunnable action) {
            return DailySettlementWorkUnits.atomic(
                    name, action, () -> {}, Integer.MAX_VALUE);
        }

        private DailySettlementWorkUnit prepared(String name) {
            return DailySettlementWorkUnits.deferred(name, () -> {
                if (!dailyProcessActive) {
                    throw new IllegalStateException(
                            "Daily process scope is not active for " + name);
                }
                DailySettlementWorkUnit work = preparedWorld.remove(name);
                if (work == null) {
                    throw new IllegalStateException("Missing prepared daily snapshot: " + name);
                }
                return work;
            });
        }

        private DailySettlementWorkUnit createDailyProcessScope(
                DailySettlementContext context) {
            List<DailySettlementWorkUnit> stages = new ArrayList<>(
                    WORLD_SNAPSHOTS.size() + 1);
            stages.add(atomic(
                    "daily_process_scope_begin", () -> beginDailyProcess(context)));
            for (String name : WORLD_SNAPSHOTS) {
                stages.add(atomic(
                        "snapshot_" + name,
                        () -> prepareWorldSnapshot(name, context)));
            }
            return DailySettlementWorkUnits.sequence(
                    "daily_process_scope", stages, () -> {});
        }

        private ServerLevel level() {
            ServerLevel level = activeLevel;
            if (level == null) {
                level = server().getLevel(com.stardew.craft.core.ModDimensions.STARDEW_VALLEY);
            }
            if (level == null) {
                throw new IllegalStateException("Stardew Valley level is not loaded");
            }
            return level;
        }

        private void beginDailyProcess(DailySettlementContext context) {
            ServerLevel level = level();
            com.stardew.craft.farm.FarmDailyProcessHelper.beginDailyProcess(
                    level, context.allOnlinePlayerIds(), context.farmOwnerIds());
            activeLevel = level;
            dailyProcessActive = true;
        }

        private void cleanupNonParticipant(
                DailySettlementContext context, UUID playerId) {
            com.stardew.craft.network.overnight.OvernightSettlementTracker.consumePayload(
                    server(), playerId, context.absoluteDay());
            com.stardew.craft.player.PassOutService.consumePassOutResult(
                    playerId, context.absoluteDay());
        }

        private void prepareWorldSnapshot(
                String name, DailySettlementContext context) {
            if (!dailyProcessActive) {
                throw new IllegalStateException(
                        "Daily process scope is not active for snapshot " + name);
            }
            if (preparedWorld.containsKey(name)) {
                throw new IllegalStateException("World snapshot is already prepared: " + name);
            }
            preparedWorld.put(name, createWorldSnapshot(name, context));
        }

        private DailySettlementWorkUnit createWorldSnapshot(
                String name, DailySettlementContext context) {
            return switch (name) {
                case "crops" -> com.stardew.craft.manager.CropGrowthManager.get(level())
                        .createDailyWorkUnit(level(), context);
                case "trees" -> com.stardew.craft.manager.TreeGrowthManager.get(level())
                        .createDailyWorkUnit(level(), context);
                case "fruit_trees" -> com.stardew.craft.manager.FruitTreeGrowthManager.get(level())
                        .createDailyWorkUnit(level(), context);
                case "tea_bushes" -> com.stardew.craft.manager.TeaBushManager.get(level())
                        .createDailyWorkUnit(level(), context);
                case "wild_tree_seeds" -> com.stardew.craft.manager.WildTreeSeedManager.get(level())
                        .createDailyWorkUnit(level(), context);
                case "farm_debris" -> com.stardew.craft.farm.FarmDebrisDailyService
                        .createDailyWorkUnit(level(), context, frozenFarms);
                case "sprinklers" -> com.stardew.craft.manager.SprinklerManager.get(level())
                        .createDailyWorkUnit(level(), context);
                case "pasture_grass" -> com.stardew.craft.manager.PastureGrassGrowthManager.get(level())
                        .createDailyWorkUnit(level(), context, frozenFarms);
                case "animals" -> com.stardew.craft.manager.AnimalGrowthManager.get(level())
                        .createDailyWorkUnit(level(), context);
                case "fish_ponds" -> com.stardew.craft.fishpond.service.FishPondDailyUpdateService
                        .createDailyWorkUnit(level(), context);
                case "public_forage" -> com.stardew.craft.manager.ForageSpawnService
                        .createDailyWorkUnit(level(), context);
                case "forest_farm_forage" -> com.stardew.craft.manager.ForageSpawnService
                        .createForestFarmDailyWorkUnit(level(), context);
                case "artifact_spots" -> com.stardew.craft.manager.ArtifactSpotSpawnService
                        .createDailyWorkUnit(level(), context);
                case "quarry" -> com.stardew.craft.manager.QuarrySpawnService
                        .createDailyWorkUnit(level(), context);
                case "coal_forest" -> com.stardew.craft.manager.CoalForestClumpSpawnService
                        .createDailyWorkUnit(level(), context);
                case "farm_caves" -> com.stardew.craft.manager.FarmCaveDailyService
                        .createDailyWorkUnit(level(), context, frozenFarms);
                case "addon_farm_tasks" -> com.stardew.craft.farm.AddonFarmDailyTaskService
                        .createDailyWorkUnit(level(), context, frozenFarms);
                default -> throw new IllegalArgumentException("Unknown world snapshot: " + name);
            };
        }

        private void lockBarrier(DailySettlementContext context) {
            lockBarrierAtStart(
                    context, barrier, accessGuard, metrics, this::sendBarrierState);
        }

        private boolean sendBarrierState(UUID playerId, int absoluteDay, boolean locked) {
            net.minecraft.server.level.ServerPlayer player =
                    server().getPlayerList().getPlayer(playerId);
            if (player == null) {
                return false;
            }
            if (locked) {
                accessGuard.captureAnchor(player);
            }
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                    player,
                    new com.stardew.craft.network.overnight.OvernightBarrierPayload(
                            absoluteDay, locked));
            return true;
        }

        private void festivalAndSeason(DailySettlementContext context) {
            ServerLevel level = level();
            com.stardew.craft.festival.FestivalService.onNewDay(level);
            if (context.seasonChanged()) {
                com.stardew.craft.farm.PublicAreaBlockTracker.get().restoreAll(level);
                com.stardew.craft.block.nature.WildWeedsBlock.refreshLoadedWeedsForSeason(
                        level, context.season());
                com.stardew.craft.manager.JunimoGreenhouseRuneManager.get(level)
                        .removeExpiredRunes(level, context.season());
            }
        }

        private DailySettlementWorkUnit createWeatherAndNpcWorkUnit(
                DailySettlementContext context) {
            DailySettlementWorkUnit weather = atomic(
                    "weather_npc_reset_weather",
                    () -> com.stardew.craft.weather.WeatherManager.applyWeatherForNewDay(
                            level(), context.day(), seasonName(context.season()),
                            context.absoluteDay()));
            DailySettlementWorkUnit npcReset = DailySettlementWorkUnits.deferred(
                    "npc_daily_reset",
                    () -> com.stardew.craft.npc.runtime.NpcSpawnManager
                            .createScheduledNpcResetWorkUnit(level()));
            return DailySettlementWorkUnits.sequence(
                    "weather_npc_reset",
                    List.of(weather, npcReset),
                    () -> {});
        }

        private DailySettlementWorkUnit createDialogueEventsDaily() {
            com.stardew.craft.npc.runtime.NpcDialogueEventData events =
                    com.stardew.craft.npc.runtime.NpcDialogueEventData.get(server());
            return DailySettlementWorkUnits.cursor(
                    "npc_dialogue_events",
                    events.playerIdsSnapshot(),
                    UUID::toString,
                    events::onNewDay,
                    () -> {});
        }

        private void dialogueTopicsDaily(DailySettlementContext context) {
            int previousAbsoluteDay = Math.max(1, context.absoluteDay() - 1);
            int previousYear = (previousAbsoluteDay - 1) / 112 + 1;
            com.stardew.craft.npc.runtime.NpcDialogueTopicService.onNewDay(
                    server(), context.previousWeather(), previousYear);
        }

        private void forecast(DailySettlementContext context) {
            com.stardew.craft.weather.WeatherManager.updateWeatherForNewDay(
                    level(), context.day(), seasonName(context.season()), context.absoluteDay());
        }

        private void updateFarmCursor(DailySettlementContext context) {
            com.stardew.craft.farm.FarmInstanceRegistry registry =
                    com.stardew.craft.farm.FarmInstanceRegistry.get();
            for (com.stardew.craft.farm.FarmInstance farm : frozenFarms.values()) {
                farm.setLastOnlineDay(context.absoluteDay());
                farm.setLastOnlineSeason(context.season());
            }
            registry.setDirty();
        }

        private void freezeFarms(DailySettlementContext context) {
            if (frozenFarmDay == context.absoluteDay()) {
                return;
            }
            if (frozenFarmDay != Integer.MIN_VALUE) {
                throw new IllegalStateException("A different farm snapshot is still active");
            }
            frozenFarms = snapshotFarms(
                    context, com.stardew.craft.farm.FarmInstanceRegistry.get());
            frozenFarmDay = context.absoluteDay();
        }

        private DailySettlementWorkUnit publication(DailySettlementContext context) {
            com.stardew.craft.time.StardewTimeManager time =
                    com.stardew.craft.time.StardewTimeManager.get();
            return publishDate(
                    context,
                    time,
                    () -> com.stardew.craft.event.DimensionEventHandler
                            .publishSettlementVirtualTime(
                                    time, context.absoluteDay()),
                    () -> com.stardew.craft.event.DimensionEventHandler
                            .onSettlementDatePublished(server(), time));
        }

        private synchronized void cleanupDailyProcess() {
            clearFrozenFarms();
            if (!dailyProcessActive) {
                return;
            }
            ServerLevel level = activeLevel;
            try {
                closePreparedWorld();
            } finally {
                try {
                    com.stardew.craft.farm.FarmDailyProcessHelper.endDailyProcess(level);
                } finally {
                    dailyProcessActive = false;
                    activeLevel = null;
                }
            }
        }

        private void clearFrozenFarms() {
            frozenFarms = Map.of();
            frozenFarmDay = Integer.MIN_VALUE;
        }

        private void closePreparedWorld() {
            Throwable failure = null;
            try {
                for (DailySettlementWorkUnit work : preparedWorld.values()) {
                    try {
                        work.close();
                    } catch (RuntimeException | Error closeFailure) {
                        if (failure == null) {
                            failure = closeFailure;
                        } else if (failure != closeFailure) {
                            failure.addSuppressed(closeFailure);
                        }
                    }
                }
            } finally {
                preparedWorld.clear();
            }
            if (failure instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (failure instanceof Error error) {
                throw error;
            }
        }

        private MinecraftServer server() {
            MinecraftServer current = server.get();
            if (current == null) {
                throw new IllegalStateException("Daily settlement server is no longer available");
            }
            return current;
        }

        private static String seasonName(int season) {
            return switch (season) {
                case 0 -> "Spring";
                case 1 -> "Summer";
                case 2 -> "Fall";
                case 3 -> "Winter";
                default -> throw new IllegalArgumentException("Invalid season: " + season);
            };
        }
    }

    static DailySettlementWorkUnit publishDate(
            DailySettlementContext context,
            com.stardew.craft.time.StardewTimeManager time,
            DailySettlementWorkUnits.ThrowingRunnable publishVirtualTime,
            DailySettlementWorkUnits.ThrowingRunnable runPublicationHooks) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(time, "time");
        Objects.requireNonNull(publishVirtualTime, "publishVirtualTime");
        Objects.requireNonNull(runPublicationHooks, "runPublicationHooks");
        return new DailySettlementWorkUnit() {
            private int stage;

            @Override
            public String name() {
                return "date_publication";
            }

            @Override
            public String currentItemIdentity() {
                return switch (stage) {
                    case 0 -> "virtual_day";
                    case 1 -> "publication_hooks";
                    case 2 -> "backing_date";
                    default -> name();
                };
            }

            @Override
            public boolean isComplete() {
                return stage >= 3;
            }

            @Override
            public void runNext() throws Exception {
                if (isComplete()) {
                    throw new IllegalStateException("Work unit is already complete: " + name());
                }
                if (stage == 0) {
                    publishVirtualTime.run();
                    stage = 1;
                }
                if (stage == 1) {
                    runPublicationHooks.run();
                    stage = 2;
                }
                if (stage == 2) {
                    time.publishSettlementDate(context);
                    stage = 3;
                }
            }

            @Override
            public void skipFailedItem() {
                throw new IllegalStateException("Date publication cannot be skipped");
            }

            @Override
            public int maxRetries() {
                return Integer.MAX_VALUE;
            }
        };
    }

    static <T> Set<UUID> dailyScopeAudience(
            List<T> onlinePlayers, Function<? super T, UUID> playerId) {
        Objects.requireNonNull(onlinePlayers, "onlinePlayers");
        Objects.requireNonNull(playerId, "playerId");
        return onlinePlayers.stream()
                .map(playerId)
                .map(id -> Objects.requireNonNull(id, "playerId"))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    static DailySettlementWorkUnit createNonParticipantCleanupWorkUnit(
            DailySettlementContext context,
            DailySettlementWorkUnits.ThrowingConsumer<UUID> cleanup) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(cleanup, "cleanup");
        return DailySettlementWorkUnits.cursor(
                "non_participant_cleanup",
                context.nonParticipantCleanupIds(),
                UUID::toString,
                cleanup,
                () -> {});
    }

    public static Set<UUID> farmOwnerAudience(
            List<UUID> participantIds, Function<? super UUID, UUID> ownerForPlayer) {
        Objects.requireNonNull(participantIds, "participantIds");
        Objects.requireNonNull(ownerForPlayer, "ownerForPlayer");
        return participantIds.stream()
                .map(playerId -> Objects.requireNonNull(playerId, "playerId"))
                .map(ownerForPlayer)
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
}
