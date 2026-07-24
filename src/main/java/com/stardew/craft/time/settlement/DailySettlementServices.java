package com.stardew.craft.time.settlement;

import net.minecraft.server.MinecraftServer;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

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

    public static synchronized void remove(MinecraftServer server) {
        if (server == null) {
            return;
        }
        Services services = SERVICES.get(server);
        if (services != null) {
            services.stop();
            SERVICES.remove(server);
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
        PlayerDailySettlementService players = new PlayerDailySettlementService(server);
        DailySettlementCommitHooks commitHooks =
                DailySettlementCommitHooks.production(server);
        DailySettlementPlanFactory plan =
                new DailySettlementPlanFactory(server, barrier, players, commitHooks);
        DailySettlementCoordinator coordinator = new DailySettlementCoordinator(
                new BudgetedWorkRunner(System::nanoTime),
                () -> 2_000_000L,
                () -> 64,
                plan,
                DailySettlementReadyPublisher.production(
                        server, barrier, players, commitHooks));
        return new Services(coordinator, barrier, plan, players);
    }

    public record Services(
            DailySettlementCoordinator coordinator,
            DailySettlementBarrier barrier,
            DailySettlementPlanFactory plan,
            PlayerDailySettlementService players) {

        public Services {
            Objects.requireNonNull(coordinator, "coordinator");
            Objects.requireNonNull(barrier, "barrier");
            Objects.requireNonNull(plan, "plan");
            Objects.requireNonNull(players, "players");
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
                    barrier.clear();
                    players.clear();
                }
            }
        }
    }

}
