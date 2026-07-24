package com.stardew.craft.time.settlement;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.WeakHashMap;
import java.lang.ref.WeakReference;

public final class DailySettlementServices {
    private static final WeakHashMap<MinecraftServer, Services> SERVICES = new WeakHashMap<>();

    private DailySettlementServices() {
    }

    public static synchronized Services get(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        return SERVICES.computeIfAbsent(server, DailySettlementServices::create);
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
                new ReadyPublisher(server, barrier, players, commitHooks));
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
                coordinator.drain();
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

    private static final class ReadyPublisher
            implements DailySettlementCoordinator.LifecycleListener {
        private final WeakReference<MinecraftServer> server;
        private final DailySettlementBarrier barrier;
        private final PlayerDailySettlementService players;
        private final DailySettlementCommitHooks commitHooks;

        private ReadyPublisher(
                MinecraftServer server,
                DailySettlementBarrier barrier,
                PlayerDailySettlementService players,
                DailySettlementCommitHooks commitHooks) {
            this.server = new WeakReference<>(server);
            this.barrier = barrier;
            this.players = players;
            this.commitHooks = commitHooks;
        }

        @Override
        public void phaseChanged(DailySettlementContext context, DailySettlementPhase phase) {
        }

        @Override
        public void itemFailure(
                DailySettlementContext context,
                String unitName,
                String itemIdentity,
                int attempt,
                boolean permanent) {
            com.stardew.craft.StardewCraft.LOGGER.error(
                    "[DAILY] unit={} item={} attempt={} permanent={}",
                    unitName, itemIdentity, attempt, permanent);
        }

        @Override
        public void ready(DailySettlementContext context) {
            commitHooks.ready(context);
            for (UUID playerId : context.playerIds()) {
                DailySettlementBarrier.ReadyResult result =
                        players.readyResultOrCreate(context, playerId);
                if (!barrier.publishReady(playerId, result)
                        && barrier.readyResult(playerId, context.absoluteDay()) != result) {
                    throw new IllegalStateException(
                            "Unable to publish settlement result for " + playerId);
                }
                ServerPlayer player = server().getPlayerList().getPlayer(playerId);
                if (player != null) {
                    PacketDistributor.sendToPlayer(player, result.payload());
                    com.stardew.craft.cutscene.server.WakeUpEventScheduler
                            .enqueueAtNightSettlement(player);
                }
            }
        }

        private MinecraftServer server() {
            MinecraftServer current = server.get();
            if (current == null) {
                throw new IllegalStateException("Daily settlement server is no longer available");
            }
            return current;
        }
    }
}
