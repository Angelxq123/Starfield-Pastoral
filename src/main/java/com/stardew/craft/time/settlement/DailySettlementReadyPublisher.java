package com.stardew.craft.time.settlement;

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
    private final Set<UUID> woken = new java.util.HashSet<>();
    private int retainedDay = -1;
    private boolean hooksPrepared;
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
                return players.readyResultOrCreate(context, playerId);
            }

            @Override
            public boolean isOnline(UUID playerId) {
                return player(playerId) != null;
            }

            @Override
            public void send(UUID playerId, OvernightSettlementPayload payload) {
                PacketDistributor.sendToPlayer(requirePlayer(playerId), payload);
            }

            @Override
            public void wake(UUID playerId) {
                com.stardew.craft.cutscene.server.WakeUpEventScheduler
                        .enqueueAtNightSettlement(requirePlayer(playerId));
            }

            private ServerPlayer player(UUID playerId) {
                MinecraftServer current = reference.get();
                if (current == null) {
                    throw new IllegalStateException(
                            "Daily settlement server is no longer available");
                }
                return current.getPlayerList().getPlayer(playerId);
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
        }, Objects.requireNonNull(metrics, "metrics"));
    }

    @Override
    public void phaseChanged(
            DailySettlementContext context, DailySettlementPhase phase) {
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
        for (UUID playerId : lockedPlayers) {
            retainedResults.computeIfAbsent(playerId, ignored ->
                    participants.contains(playerId)
                            ? operations.prepareResult(context, playerId)
                            : DailySettlementBarrier.ReadyResult.barrierOnly(
                                    context.absoluteDay()));
        }
        if (!barrierPublished) {
            if (!barrier.publishReadyAll(context.absoluteDay(), retainedResults)) {
                throw new IllegalStateException(
                        "Unable to atomically publish settlement results for day "
                                + context.absoluteDay());
            }
            barrierPublished = true;
        }
        for (UUID playerId : lockedPlayers) {
            if (!barrier.isLocked(playerId)) {
                sent.add(playerId);
                woken.add(playerId);
                continue;
            }
            if (!operations.isOnline(playerId)) {
                continue;
            }
            if (!sent.contains(playerId)) {
                operations.send(playerId, retainedResults.get(playerId).payload());
                sent.add(playerId);
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
        woken.clear();
        hooksPrepared = false;
        barrierPublished = false;
    }

    interface Operations {
        void prepareHooks(DailySettlementContext context);

        DailySettlementBarrier.ReadyResult prepareResult(
                DailySettlementContext context, UUID playerId);

        boolean isOnline(UUID playerId);

        void send(UUID playerId, OvernightSettlementPayload payload);

        void wake(UUID playerId);
    }
}
