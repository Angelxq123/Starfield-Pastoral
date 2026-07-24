package com.stardew.craft.time.settlement;

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
            "daily_process_scope",
            "settlement_barrier_lock");
    private static final List<String> WORLD = List.of(
            "festival_season_prep",
            "weather_npc_reset",
            "crops",
            "trees",
            "fruit_trees",
            "wild_tree_seeds",
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
            "farm_caves");
    private static final List<String> PLAYERS = List.of("player_daily_settlement");
    private static final List<String> COMMIT = List.of(
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
        this(new ProductionWorkUnitFactory(server, barrier, players));
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

    @FunctionalInterface
    public interface WorkUnitFactory {
        DailySettlementWorkUnit create(String name, DailySettlementContext context);

        default void cleanup() {
        }
    }

    private static final class ProductionWorkUnitFactory implements WorkUnitFactory {
        private final WeakReference<MinecraftServer> server;
        private final DailySettlementBarrier barrier;
        private final PlayerDailySettlementService players;
        private final Map<String, DailySettlementWorkUnit> preparedWorld =
                new LinkedHashMap<>();
        private Map<UUID, com.stardew.craft.farm.FarmInstance> frozenFarms = Map.of();
        private int frozenFarmDay = Integer.MIN_VALUE;
        private ServerLevel activeLevel;
        private boolean dailyProcessActive;

        private ProductionWorkUnitFactory(
                MinecraftServer server,
                DailySettlementBarrier barrier,
                PlayerDailySettlementService players) {
            this.server = new WeakReference<>(Objects.requireNonNull(server, "server"));
            this.barrier = Objects.requireNonNull(barrier, "barrier");
            this.players = Objects.requireNonNull(players, "players");
        }

        @Override
        public DailySettlementWorkUnit create(String name, DailySettlementContext context) {
            freezeFarms(context);
            return switch (name) {
                case "shipping_bin_flush" -> atomic(name,
                        com.stardew.craft.blockentity.ShippingBinBlockEntity::flushAllForOvernight);
                case "non_participant_cleanup" -> createNonParticipantCleanupWorkUnit(
                        context, playerId -> cleanupNonParticipant(context, playerId));
                case "daily_process_scope" -> atomic(name, () -> beginDailyProcess(context));
                case "settlement_barrier_lock" -> atomic(name, () -> lockBarrier(context));
                case "festival_season_prep" -> atomic(name, () -> festivalAndSeason(context));
                case "weather_npc_reset" -> atomic(name, () -> weatherAndNpcs(context));
                case "crops", "trees", "fruit_trees", "wild_tree_seeds",
                        "sprinklers", "pasture_grass", "animals", "fish_ponds",
                        "public_forage", "forest_farm_forage", "artifact_spots",
                        "quarry", "coal_forest", "farm_caves" -> prepared(name);
                case "secret_woods_entrance" -> atomic(name, () ->
                        com.stardew.craft.manager.SecretWoodsAccessManager.ensureEntranceReady(level()));
                case "player_daily_settlement" -> players.createDailyWorkUnit(context);
                case "weather_forecast" -> atomic(name, () -> forecast(context));
                case "farm_cursor" -> atomic(name, () -> updateFarmCursor(context));
                case "special_orders" -> atomic(name, () -> specialOrders(context));
                case "lost_and_found" -> atomic(name, () ->
                        com.stardew.craft.lostandfound.LostAndFoundService.onNewDay(level()));
                case "bookseller" -> atomic(name, () -> bookseller(context));
                case "shop_stock" -> atomic(name,
                        com.stardew.craft.shop.ShopStockTracker::resetForNewDay);
                case "mail" -> atomic(name, () -> mail(context));
                case "dirty_mark" -> atomic(name, () ->
                        com.stardew.craft.farm.FarmInstanceRegistry.get().setDirty());
                case "daily_process_cleanup" -> requiredAtomic(name, this::cleanupDailyProcess);
                case "date_publication" -> publication(context);
                default -> throw new IllegalArgumentException("Unknown settlement work unit: " + name);
            };
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
                    level, context.allOnlinePlayerIds());
            activeLevel = level;
            dailyProcessActive = true;
            try {
                prepareWorldSnapshots(context);
            } catch (RuntimeException | Error failure) {
                try {
                    cleanupDailyProcess();
                } catch (RuntimeException | Error cleanupFailure) {
                    if (cleanupFailure != failure) {
                        failure.addSuppressed(cleanupFailure);
                    }
                }
                throw failure;
            }
        }

        private void cleanupNonParticipant(
                DailySettlementContext context, UUID playerId) {
            com.stardew.craft.network.overnight.OvernightSettlementTracker.consumePayload(
                    server(), playerId, context.absoluteDay());
            com.stardew.craft.player.PassOutService.consumePassOutResult(
                    playerId, context.absoluteDay());
        }

        private void prepareWorldSnapshots(DailySettlementContext context) {
            if (!preparedWorld.isEmpty()) {
                throw new IllegalStateException("World snapshots are already prepared");
            }
            for (String name : List.of(
                    "crops", "trees", "fruit_trees", "wild_tree_seeds",
                    "sprinklers", "pasture_grass", "animals", "fish_ponds",
                    "public_forage", "forest_farm_forage", "artifact_spots",
                    "quarry", "coal_forest", "farm_caves")) {
                preparedWorld.put(name, createWorldSnapshot(name, context));
            }
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
                case "wild_tree_seeds" -> com.stardew.craft.manager.WildTreeSeedManager.get(level())
                        .createDailyWorkUnit(level(), context);
                case "sprinklers" -> com.stardew.craft.manager.SprinklerManager.get(level())
                        .createDailyWorkUnit(level(), context);
                case "pasture_grass" -> com.stardew.craft.manager.PastureGrassGrowthManager.get(level())
                        .createDailyWorkUnit(level(), context);
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
                default -> throw new IllegalArgumentException("Unknown world snapshot: " + name);
            };
        }

        private void lockBarrier(DailySettlementContext context) {
            barrier.lockAll(context.absoluteDay(), context.playerIds());
            for (UUID playerId : context.playerIds()) {
                net.minecraft.server.level.ServerPlayer player =
                        server().getPlayerList().getPlayer(playerId);
                if (player != null) {
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                            player,
                            new com.stardew.craft.network.overnight.OvernightBarrierPayload(
                                    context.absoluteDay(), true));
                }
            }
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

        private void weatherAndNpcs(DailySettlementContext context) {
            com.stardew.craft.weather.WeatherManager.applyWeatherForNewDay(
                    level(), context.day(), seasonName(context.season()), context.absoluteDay());
            com.stardew.craft.npc.runtime.NpcSpawnManager.resetScheduledNpcsForNewDay(level());
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

        private void bookseller(DailySettlementContext context) {
            com.stardew.craft.book.BooksellerSchedule.onNewDayForPlayers(
                    level(), context.valleyOnlinePlayerIds(), context.year(),
                    context.season(), context.day(), context.absoluteDay());
            com.stardew.craft.shop.BooksellerEvents.forceCheckNow(level());
        }

        private void specialOrders(DailySettlementContext context) {
            com.stardew.craft.specialorder.SpecialOrderManager.onNewDayForPlayers(
                    level(), context.allOnlinePlayerIds());
        }

        private void mail(DailySettlementContext context) {
            com.stardew.craft.mail.MailService.deliverTomorrowMailForPlayers(
                    server(), context.allOnlinePlayerIds());
            com.stardew.craft.time.StardewTimeManager time =
                    com.stardew.craft.time.StardewTimeManager.get();
            time.scheduleDateTriggeredMailForPlayers(
                    server(), context.allOnlinePlayerIds(), context.absoluteDay());
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
