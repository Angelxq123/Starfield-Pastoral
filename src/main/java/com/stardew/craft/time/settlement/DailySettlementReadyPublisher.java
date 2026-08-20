package com.stardew.craft.time.settlement;

import com.stardew.craft.Config;
import com.stardew.craft.network.overnight.OvernightSettlementPayload;
import com.stardew.craft.server.performance.DailySettlementMetrics;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.lang.ref.WeakReference;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

final class DailySettlementReadyPublisher
        implements DailySettlementCoordinator.LifecycleListener {
    private final DailySettlementBarrier barrier;
    private final Operations operations;
    private final DailySettlementMetrics metrics;
    private final Map<UUID, DailySettlementBarrier.ReadyResult> retainedResults =
            new LinkedHashMap<>();
    private final Set<UUID> sent = new java.util.HashSet<>();
    private final Set<UUID> worldReadySent = new java.util.HashSet<>();
    private final Set<UUID> woken = new java.util.HashSet<>();
    private int retainedDay = -1;
    private boolean hooksPrepared;
    private boolean deliveryPrepared;
    private boolean barrierPublished;

    DailySettlementReadyPublisher(
            DailySettlementBarrier barrier, Operations operations) {
        this(barrier, operations, null);
    }

    DailySettlementReadyPublisher(
            DailySettlementBarrier barrier,
            Operations operations,
            DailySettlementMetrics metrics) {
        this.barrier = Objects.requireNonNull(barrier, "barrier");
        this.operations = Objects.requireNonNull(operations, "operations");
        this.metrics = metrics;
    }

    static DailySettlementReadyPublisher production(
            MinecraftServer server,
            DailySettlementBarrier barrier,
            PlayerDailySettlementService players,
            DailySettlementCommitHooks commitHooks,
            DailySettlementMetrics metrics) {
        WeakReference<MinecraftServer> reference =
                new WeakReference<>(Objects.requireNonNull(server, "server"));
        return new DailySettlementReadyPublisher(barrier, new Operations() {
            @Override
            public void prepareHooks(DailySettlementContext context) {
                commitHooks.ready(context);
            }

            @Override
            public DailySettlementBarrier.ReadyResult prepareResult(
                    DailySettlementContext context, UUID playerId) {
                return players.prepareResult(context, playerId);
            }

            @Override
            public boolean isOnline(UUID playerId) {
                return player(playerId) != null;
            }

            @Override
            public void send(UUID playerId, OvernightSettlementPayload payload) {
                ServerPlayer player = requirePlayer(playerId);
                com.stardew.craft.time.StardewTimePauseService
                        .beginOvernightSettlement(player);
                if (Config.isSettlementDebugLoggingEnabled()) {
                    com.stardew.craft.StardewCraft.LOGGER.info(
                            "[OVERNIGHT_SERVER] Sending settlement player={} day={} personal={} shipped={} levels={} passOut={}",
                            player.getGameProfile().getName(), payload.absoluteDay(),
                            payload.personalSettlement(), payload.shippedItems().size(),
                            payload.levelUps().size(), payload.hasPassOut());
                }
                PacketDistributor.sendToPlayer(player, payload);
            }

            @Override
            public void wake(UUID playerId) {
                com.stardew.craft.cutscene.server.WakeUpEventScheduler
                        .enqueueAtNightSettlement(requirePlayer(playerId));
            }

            @Override
            public void worldReady(UUID playerId, int absoluteDay) {
                PacketDistributor.sendToPlayer(
                        requirePlayer(playerId),
                        new com.stardew.craft.network.overnight.OvernightWorldReadyPayload(
                                absoluteDay));
            }

            @Override
            public void prepareDelivery(
                    DailySettlementContext context,
                    Map<UUID, DailySettlementBarrier.ReadyResult> results) {
                MinecraftServer current = server();
                for (Map.Entry<UUID, DailySettlementBarrier.ReadyResult> entry
                        : results.entrySet()) {
                    OvernightSettlementPayload payload = entry.getValue().payload();
                    if (payload.personalSettlement()) {
                        com.stardew.craft.network.overnight.OvernightSettlementTracker
                                .storePendingSettlement(
                                        current, entry.getKey(), payload);
                    }
                }
                com.stardew.craft.player.PlayerDataManager.get().setDirty();
                // The tracker and player data are already marked dirty above and by
                // PlayerDailySettlementService. Do not force a full world save here:
                // it synchronously flushes every loaded chunk on the server thread
                // before READY can reach the sleeping client.
            }

            private ServerPlayer player(UUID playerId) {
                return server().getPlayerList().getPlayer(playerId);
            }

            private ServerPlayer requirePlayer(UUID playerId) {
                ServerPlayer player = player(playerId);
                if (player == null) {
                    throw new IllegalStateException(
                            "Settlement player went offline during READY delivery: "
                                    + playerId);
                }
                return player;
            }

            private MinecraftServer server() {
                MinecraftServer current = reference.get();
                if (current == null) {
                    throw new IllegalStateException(
                            "Daily settlement server is no longer available");
                }
                return current;
            }
        }, Objects.requireNonNull(metrics, "metrics"));
    }

    @Override
    public void phaseChanged(
            DailySettlementContext context, DailySettlementPhase phase) {
    }

    @Override
    public void playerResultsReady(DailySettlementContext context) {
        beginDay(context.absoluteDay());
        Set<UUID> participants = Set.copyOf(context.playerIds());
        Set<UUID> lockedPlayers = orderedLockedPlayers(context);
        prepareResults(context, participants, lockedPlayers);
        deliverPlayerResults(context, lockedPlayers);
    }

    @Override
    public void itemFailure(
            DailySettlementContext context,
            String unitName,
            String itemIdentity,
            int attempt,
            boolean permanent) {
    }

    @Override
    public void ready(DailySettlementContext context) {
        beginDay(context.absoluteDay());
        if (!hooksPrepared) {
            operations.prepareHooks(context);
            hooksPrepared = true;
        }
        Set<UUID> participants = Set.copyOf(context.playerIds());
        Set<UUID> lockedPlayers = orderedLockedPlayers(context);
        prepareResults(context, participants, lockedPlayers);
        if (!deliveryPrepared) {
            operations.prepareDelivery(context, Map.copyOf(retainedResults));
            deliveryPrepared = true;
        }
        if (!barrierPublished) {
            if (!barrier.publishReadyAll(context.absoluteDay(), retainedResults)) {
                throw new IllegalStateException(
                        "Unable to atomically publish settlement results for day "
                                + context.absoluteDay());
            }
            barrierPublished = true;
        }
        deliverPlayerResults(context, lockedPlayers);
        for (UUID playerId : lockedPlayers) {
            if (!barrier.isLocked(playerId)) {
                if (sent.contains(playerId) && !woken.contains(playerId)
                        && operations.isOnline(playerId)) {
                    operations.wake(playerId);
                    woken.add(playerId);
                }
                sent.add(playerId);
                continue;
            }
            if (!operations.isOnline(playerId)) {
                continue;
            }
            if (!worldReadySent.contains(playerId)) {
                operations.worldReady(playerId, context.absoluteDay());
                worldReadySent.add(playerId);
            }
            if (!woken.contains(playerId)) {
                operations.wake(playerId);
                woken.add(playerId);
            }
        }
        if (metrics != null) {
            DailySettlementMetrics.ReadySummary summary = metrics.completeReady();
            metrics.publishReady(summary);
        }
    }

    private void prepareResults(
            DailySettlementContext context,
            Set<UUID> participants,
            Set<UUID> lockedPlayers) {
        for (UUID playerId : lockedPlayers) {
            retainedResults.computeIfAbsent(playerId, ignored ->
                    participants.contains(playerId)
                            ? operations.prepareResult(context, playerId)
                            : DailySettlementBarrier.ReadyResult.barrierOnly(
                                    context.absoluteDay()));
        }
        if (!deliveryPrepared) {
            operations.prepareDelivery(context, Map.copyOf(retainedResults));
            deliveryPrepared = true;
        }
    }

    private void deliverPlayerResults(
            DailySettlementContext context, Set<UUID> lockedPlayers) {
        for (UUID playerId : lockedPlayers) {
            if (!barrier.isLocked(playerId) || !operations.isOnline(playerId)
                    || sent.contains(playerId)) {
                continue;
            }
            operations.send(playerId, retainedResults.get(playerId).payload());
            sent.add(playerId);
        }
    }

    private Set<UUID> orderedLockedPlayers(DailySettlementContext context) {
        LinkedHashSet<UUID> ordered = new LinkedHashSet<>();
        for (UUID playerId : context.playerIds()) {
            if (barrier.lockedDay(playerId) == context.absoluteDay()) {
                ordered.add(playerId);
            }
        }
        barrier.lockedPlayerIds(context.absoluteDay()).stream()
                .filter(playerId -> !ordered.contains(playerId))
                .sorted()
                .forEach(ordered::add);
        return ordered;
    }

    private void beginDay(int absoluteDay) {
        if (retainedDay == absoluteDay) {
            return;
        }
        retainedDay = absoluteDay;
        retainedResults.clear();
        sent.clear();
        worldReadySent.clear();
        woken.clear();
        hooksPrepared = false;
        deliveryPrepared = false;
        barrierPublished = false;
    }

    interface Operations {
        void prepareHooks(DailySettlementContext context);

        default void prepareDelivery(
                DailySettlementContext context,
                Map<UUID, DailySettlementBarrier.ReadyResult> results) {
        }

        DailySettlementBarrier.ReadyResult prepareResult(
                DailySettlementContext context, UUID playerId);

        boolean isOnline(UUID playerId);

        void send(UUID playerId, OvernightSettlementPayload payload);

        default void worldReady(UUID playerId, int absoluteDay) {
        }

        void wake(UUID playerId);
    }
}
