package com.stardew.craft.time.settlement;

import net.minecraft.server.MinecraftServer;
import com.stardew.craft.Config;
import com.stardew.craft.server.performance.DailySettlementMetrics;
import java.util.concurrent.TimeUnit;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.server.level.ServerPlayer;

public final class DailySettlementServices {
    private static final WeakKeyRegistry<MinecraftServer, Services> SERVICES =
            new WeakKeyRegistry<>();

    private DailySettlementServices() {
    }

    public static synchronized Services get(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        return SERVICES.getOrCreate(server, DailySettlementServices::create);
    }

    public static synchronized Services find(MinecraftServer server) {
        return server == null ? null : SERVICES.get(server);
    }

    public static synchronized Services getForPlayer(ServerPlayer player) {
        Objects.requireNonNull(player, "player");
        Services services = get(player.server);
        restorePlayerBarrier(
                services.players(), services.barrier(), services.coordinator(), player.getUUID());
        return services;
    }

    static Optional<DailySettlementBarrier.ReadyResult> restorePlayerBarrier(
            PlayerDailySettlementService players,
            DailySettlementBarrier barrier,
            DailySettlementCoordinator coordinator,
            UUID playerId) {
        Objects.requireNonNull(players, "players");
        Objects.requireNonNull(barrier, "barrier");
        Objects.requireNonNull(coordinator, "coordinator");
        Objects.requireNonNull(playerId, "playerId");
        boolean coordinatorOwns = coordinator.context()
                .map(context -> context.playerIds().contains(playerId))
                .orElse(false);
        return coordinatorOwns
                ? Optional.empty()
                : players.onLogin(playerId, barrier);
    }

    public static synchronized void remove(MinecraftServer server) {
        if (server == null) {
            return;
        }
        removeRegistered(SERVICES, server, Services::stop);
    }

    static <K, V> void removeRegistered(
            WeakKeyRegistry<K, V> registry, K key, Consumer<V> stop) {
        Objects.requireNonNull(registry, "registry");
        Objects.requireNonNull(stop, "stop");
        V value = registry.get(Objects.requireNonNull(key, "key"));
        if (value == null) {
            return;
        }
        try {
            stop.accept(value);
        } finally {
            registry.remove(key);
        }
    }

    public static boolean ownsSettlement(Services services, UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return services != null && ownsSettlement(
                playerId,
                services.coordinator().context(),
                services.barrier().isLocked(playerId),
                services.players().pendingSettlement(playerId).isPresent());
    }

    static boolean ownsSettlement(
            UUID playerId,
            Optional<DailySettlementContext> activeContext,
            boolean locked,
            boolean pending) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(activeContext, "activeContext");
        return locked || pending || activeContext
                .map(context -> context.playerIds().contains(playerId))
                .orElse(false);
    }

    private static Services create(MinecraftServer server) {
        DailySettlementBarrier barrier = new DailySettlementBarrier();
        DailySettlementAccessGuard accessGuard = new DailySettlementAccessGuard(barrier);
        DailySettlementMetrics metrics = DailySettlementMetrics.production();
        PlayerDailySettlementService players = new PlayerDailySettlementService(server);
        DailySettlementCommitHooks commitHooks =
                DailySettlementCommitHooks.production(server);
        DailySettlementPlanFactory plan =
                new DailySettlementPlanFactory(
                        server, barrier, players, commitHooks, accessGuard, metrics);
        DailySettlementCoordinator coordinator = new DailySettlementCoordinator(
                new BudgetedWorkRunner(System::nanoTime),
                () -> TimeUnit.MILLISECONDS.toNanos(
                        Config.DAILY_SETTLEMENT_BUDGET_MILLIS.get().longValue()),
                () -> Config.DAILY_SETTLEMENT_ITEM_LIMIT.get(),
                plan,
                DailySettlementReadyPublisher.production(
                        server, barrier, players, commitHooks, metrics),
                metrics,
                server::isSameThread);
        return new Services(coordinator, barrier, accessGuard, plan, players, metrics);
    }

    public record Services(
            DailySettlementCoordinator coordinator,
            DailySettlementBarrier barrier,
            DailySettlementAccessGuard accessGuard,
            DailySettlementPlanFactory plan,
            PlayerDailySettlementService players,
            DailySettlementMetrics metrics) {

        public Services {
            Objects.requireNonNull(coordinator, "coordinator");
            Objects.requireNonNull(barrier, "barrier");
            Objects.requireNonNull(accessGuard, "accessGuard");
            Objects.requireNonNull(plan, "plan");
            Objects.requireNonNull(players, "players");
            Objects.requireNonNull(metrics, "metrics");
        }

        public void stop() {
            try {
                if (!coordinator.drain()) {
                    com.stardew.craft.StardewCraft.LOGGER.error(
                            "[DAILY] Stop drain deadline exceeded; "
                                    + "aborted active settlement and started cleanup");
                }
            } finally {
                try {
                    plan.cleanup();
                } finally {
                    try {
                        accessGuard.clear();
                    } finally {
                        barrier.clear();
                        players.clear();
                    }
                }
            }
        }
    }

}
