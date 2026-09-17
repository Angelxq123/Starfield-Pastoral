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
    private final Set<UUID> deliveryPrepared = new java.util.HashSet<>();
    private final Set<UUID> preparedOffline = new java.util.HashSet<>();
    private DailySettlementWorkUnit earlyWork;
    private DailySettlementWorkUnit finalWork;
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
            public DailySettlementWorkUnit hooksWork(DailySettlementContext context) {
                return commitHooks.createReadyWorkUnit(context);
            }

            @Override
            public DailySettlementBarrier.ReadyResult prepareResult(
                    DailySettlementContext context, UUID playerId) {
                return players.prepareResult(context, playerId);
            }

            @Override
            public DailySettlementBarrier.ReadyResult prepareFinalResult(
                    DailySettlementContext context, UUID playerId) {
                // A participant may have returned after the COMMIT cleanup cursor.
                return players.readyResultOrCreate(context, playerId);
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
        runRemaining(playerResultsWork(context));
    }

    @Override
    public DailySettlementWorkUnit playerResultsWork(DailySettlementContext context) {
        beginDay(context.absoluteDay());
        if (earlyWork == null) {
            earlyWork = DailySettlementWorkUnits.deferred("player_result_publication", () ->
                    playerResultsCursor(context, "player_result_publication", true));
        }
        return earlyWork;
    }

    @Override
    public DailySettlementWorkUnit readyWork(DailySettlementContext context) {
        beginDay(context.absoluteDay());
        if (finalWork == null) {
            finalWork = DailySettlementWorkUnits.sequence("ready_publication", java.util.List.of(
                    DailySettlementWorkUnits.deferred("ready_result_preparation", () ->
                            playerResultsCursor(context, "ready_result_preparation", false)),
                    DailySettlementWorkUnits.deferred("ready_hooks", () -> operations.hooksWork(context)),
                    DailySettlementWorkUnits.atomic("ready_barrier", () -> publishBarrier(context), () -> {}),
                    readyDeliveryWork(context)), () -> {});
        }
        return finalWork;
    }

    private DailySettlementWorkUnit readyDeliveryWork(DailySettlementContext context) {
        return new DailySettlementWorkUnit() {
            private final Set<UUID> visited = new java.util.HashSet<>();
            private final java.util.ArrayDeque<UUID> queued = new java.util.ArrayDeque<>();

            private void refresh() {
                if (queued.isEmpty()) {
                    // READY now spans ticks; include locks created by intervening logins.
                    for (UUID id : orderedLockedPlayers(context)) {
                        if (!visited.contains(id)
                                || (preparedOffline.contains(id) && operations.isOnline(id))) {
                            queued.addLast(id);
                        }
                    }
                }
            }

            public String name() { return "ready_delivery"; }
            public String currentItemIdentity() {
                refresh();
                return queued.isEmpty() ? name() : queued.peekFirst().toString();
            }
            public boolean isComplete() {
                refresh();
                return queued.isEmpty();
            }
            public void runNext() {
                refresh();
                UUID id = queued.getFirst();
                deliverReady(context, id);
                visited.add(id);
                queued.removeFirst();
            }
            public void skipFailedItem() {
                throw new IllegalStateException("READY delivery must be retried");
            }
        };
    }

    private DailySettlementWorkUnit playerResultsCursor(
            DailySettlementContext context, String name, boolean early) {
        Set<UUID> participants = Set.copyOf(context.playerIds());
        return DailySettlementWorkUnits.cursor(name, orderedLockedPlayers(context), UUID::toString,
                playerId -> {
                    prepareResult(context, participants, playerId, !early);
                    if (early) {
                        deliverPlayerResult(playerId);
                    }
                }, () -> {});
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
        runRemaining(readyWork(context));
    }

    private void publishBarrier(DailySettlementContext context) {
        if (!barrierPublished) {
            if (!barrier.publishReadyAll(context.absoluteDay(), retainedResults)) {
                throw new IllegalStateException(
                        "Unable to atomically publish settlement results for day "
                                + context.absoluteDay());
            }
            barrierPublished = true;
        }
    }

    private void deliverReady(DailySettlementContext context, UUID playerId) {
        if (!barrier.isLocked(playerId)) {
            if (sent.contains(playerId) && !woken.contains(playerId)
                    && operations.isOnline(playerId)) {
                operations.wake(playerId);
                woken.add(playerId);
            }
            return;
        }
        if (!retainedResults.containsKey(playerId)
                || (preparedOffline.contains(playerId) && operations.isOnline(playerId))) {
            prepareResult(context, Set.copyOf(context.playerIds()), playerId, true);
            DailySettlementBarrier.ReadyResult refreshed = retainedResults.get(playerId);
            boolean published = barrier.readyResult(playerId, context.absoluteDay()) == null
                    ? barrier.publishReady(playerId, refreshed)
                    : barrier.replaceReady(playerId, refreshed);
            if (!published) {
                throw new IllegalStateException("Unable to publish late settlement lock " + playerId);
            }
        }
        deliverPlayerResult(playerId);
        if (!operations.isOnline(playerId)) {
            preparedOffline.add(playerId);
            return;
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

    @Override
    public void publicationComplete(DailySettlementContext context) {
        if (metrics != null) {
            DailySettlementMetrics.ReadySummary summary = metrics.completeReady();
            metrics.publishReady(summary);
        }
    }

    private void prepareResult(
            DailySettlementContext context,
            Set<UUID> participants,
            UUID playerId,
            boolean finalizing) {
        if (preparedOffline.contains(playerId) && operations.isOnline(playerId)) {
            retainedResults.remove(playerId);
            deliveryPrepared.remove(playerId);
            preparedOffline.remove(playerId);
        }
        retainedResults.computeIfAbsent(playerId, ignored ->
                participants.contains(playerId)
                        ? (finalizing ? operations.prepareFinalResult(context, playerId)
                                : operations.prepareResult(context, playerId))
                        : DailySettlementBarrier.ReadyResult.barrierOnly(
                                context.absoluteDay()));
        if (!operations.isOnline(playerId)) {
            preparedOffline.add(playerId);
        }
        if (!deliveryPrepared.contains(playerId)) {
            operations.prepareDelivery(context, Map.of(playerId, retainedResults.get(playerId)));
            deliveryPrepared.add(playerId);
        }
    }

    private void deliverPlayerResult(UUID playerId) {
        if (!barrier.isLocked(playerId) || !operations.isOnline(playerId)
                || sent.contains(playerId)) {
            return;
        }
        operations.send(playerId, retainedResults.get(playerId).payload());
        sent.add(playerId);
    }

    private static void runRemaining(DailySettlementWorkUnit work) {
        try {
            while (!work.isComplete()) {
                work.runNext();
            }
        } catch (RuntimeException | Error failure) {
            throw failure;
        } catch (Exception failure) {
            throw new IllegalStateException("Unable to publish settlement", failure);
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
        deliveryPrepared.clear();
        preparedOffline.clear();
        earlyWork = null;
        finalWork = null;
        barrierPublished = false;
    }

    interface Operations {
        void prepareHooks(DailySettlementContext context);

        default DailySettlementWorkUnit hooksWork(DailySettlementContext context) {
            return DailySettlementWorkUnits.atomic("ready_hooks", () -> prepareHooks(context), () -> {});
        }

        default void prepareDelivery(
                DailySettlementContext context,
                Map<UUID, DailySettlementBarrier.ReadyResult> results) {
        }

        DailySettlementBarrier.ReadyResult prepareResult(
                DailySettlementContext context, UUID playerId);

        default DailySettlementBarrier.ReadyResult prepareFinalResult(
                DailySettlementContext context, UUID playerId) {
            return prepareResult(context, playerId);
        }

        boolean isOnline(UUID playerId);

        void send(UUID playerId, OvernightSettlementPayload payload);

        default void worldReady(UUID playerId, int absoluteDay) {
        }

        void wake(UUID playerId);
    }
}
