package com.stardew.craft.time.settlement;

import com.stardew.craft.network.overnight.OvernightSettlementPayload;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DailySettlementReadyPublisherTest {

    @Test
    void publicationAndWorldReadyRespectTheSharedPlayerBudget() {
        List<UUID> ids = List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        DailySettlementContext context = new DailySettlementContext(
                226, 3, 0, 2, 1560, false, ids, Set.of());
        DailySettlementBarrier barrier = new DailySettlementBarrier();
        barrier.lockAll(226, ids);
        List<UUID> prepared = new ArrayList<>();
        List<UUID> sent = new ArrayList<>();
        List<UUID> notified = new ArrayList<>();
        List<Integer> persistedBatchSizes = new ArrayList<>();
        long[] clock = {0L};
        com.stardew.craft.server.performance.DailySettlementMetrics metrics =
                new com.stardew.craft.server.performance.DailySettlementMetrics(() -> clock[0], () -> 0L);
        DailySettlementReadyPublisher publisher = new DailySettlementReadyPublisher(barrier,
                new DailySettlementReadyPublisher.Operations() {
                    public void prepareHooks(DailySettlementContext target) {}
                    public DailySettlementBarrier.ReadyResult prepareResult(
                            DailySettlementContext target, UUID playerId) {
                        prepared.add(playerId);
                        clock[0] += 20L;
                        return ready(226);
                    }
                    public void prepareDelivery(DailySettlementContext target,
                            Map<UUID, DailySettlementBarrier.ReadyResult> results) {
                        persistedBatchSizes.add(results.size());
                        clock[0] += 20L;
                    }
                    public boolean isOnline(UUID id) { return true; }
                    public void send(UUID id, OvernightSettlementPayload payload) {
                        sent.add(id);
                        clock[0] += 20L;
                    }
                    public void worldReady(UUID id, int day) {
                        assertEquals(3, sent.size(), "all early results precede global READY");
                        assertNotNull(barrier.readyResult(id, day));
                        notified.add(id);
                        clock[0] += 100L;
                    }
                    public void wake(UUID id) {}
                }, metrics);
        DailySettlementCoordinator coordinator = new DailySettlementCoordinator(
                new BudgetedWorkRunner(() -> clock[0]), () -> 70L, () -> 1,
                (target, builder) -> {}, publisher, metrics, () -> true);
        coordinator.start(context);
        for (int tick = 0; coordinator.isActive() && tick < 30; tick++) {
            int beforePrepared = prepared.size();
            int beforeSent = sent.size();
            int beforeNotified = notified.size();
            coordinator.tick();
            assertTrue(prepared.size() - beforePrepared <= 1);
            assertTrue(sent.size() - beforeSent <= 1);
            assertTrue(notified.size() - beforeNotified <= 1);
        }
        assertFalse(coordinator.isActive());
        assertEquals(ids, prepared);
        assertEquals(ids, sent);
        assertEquals(ids, notified);
        assertTrue(persistedBatchSizes.stream().allMatch(size -> size == 1));
        assertTrue(metrics.readySummary().maxPerTickWorkNanos() >= 100L,
                "the final READY delivery belongs to tick work metrics");
    }

    @Test
    void partialSendAckAndWakeFailureRetryWithoutRebuildingSettlements() {
        UUID playerA = UUID.randomUUID();
        UUID playerB = UUID.randomUUID();
        DailySettlementContext context = new DailySettlementContext(
                226, 3, 0, 2, 1560, false,
                List.of(playerA, playerB), Set.of());
        DailySettlementBarrier barrier = new DailySettlementBarrier();
        barrier.lockAll(context.absoluteDay(), context.playerIds());
        Map<UUID, Integer> settlements = new HashMap<>();
        Map<UUID, Integer> sends = new HashMap<>();
        Map<UUID, Integer> wakes = new HashMap<>();
        Set<UUID> pending = new HashSet<>();
        int[] hookCalls = {0};
        int[] deliveryCalls = {0};

        DailySettlementReadyPublisher publisher = new DailySettlementReadyPublisher(
                barrier, new DailySettlementReadyPublisher.Operations() {
                    @Override
                    public void prepareHooks(DailySettlementContext target) {
                        hookCalls[0]++;
                    }

                    @Override
                    public DailySettlementBarrier.ReadyResult prepareResult(
                            DailySettlementContext target, UUID playerId) {
                        settlements.merge(playerId, 1, Integer::sum);
                        pending.add(playerId);
                        return ready(target.absoluteDay());
                    }

                    @Override
                    public void prepareDelivery(
                            DailySettlementContext target,
                            Map<UUID, DailySettlementBarrier.ReadyResult> results) {
                        deliveryCalls[0]++;
                        assertEquals(1, results.size());
                        assertTrue(Set.of(playerA, playerB).containsAll(results.keySet()));
                    }

                    @Override
                    public boolean isOnline(UUID playerId) {
                        return true;
                    }

                    @Override
                    public void send(
                            UUID playerId, OvernightSettlementPayload payload) {
                        assertTrue(deliveryCalls[0] >= 1);
                        sends.merge(playerId, 1, Integer::sum);
                    }

                    @Override
                    public void worldReady(UUID playerId, int absoluteDay) {
                        if (playerId.equals(playerA)) {
                            assertTrue(barrier.acknowledge(
                                    playerA, context.absoluteDay()));
                            pending.remove(playerA);
                        }
                    }

                    @Override
                    public void wake(UUID playerId) {
                        int attempt = wakes.merge(playerId, 1, Integer::sum);
                        if (playerId.equals(playerB) && attempt == 1) {
                            throw new IllegalStateException("injected wake failure");
                        }
                    }
                });
        DailySettlementCoordinator coordinator = new DailySettlementCoordinator(
                new BudgetedWorkRunner(() -> 0L), () -> 1_000_000L, () -> 64,
                (target, builder) -> {}, publisher);

        assertTrue(coordinator.start(context));
        coordinator.tick();

        assertEquals(DailySettlementPhase.READY, coordinator.phase());
        assertFalse(barrier.isLocked(playerA));
        assertTrue(barrier.isLocked(playerB));
        assertNotNull(barrier.readyResult(playerB, context.absoluteDay()));

        coordinator.tick();

        assertEquals(DailySettlementPhase.IDLE, coordinator.phase());
        assertEquals(Map.of(playerA, 1, playerB, 1), settlements);
        assertEquals(Map.of(playerA, 1, playerB, 1), sends);
        assertEquals(Map.of(playerA, 1, playerB, 2), wakes);
        assertEquals(1, hookCalls[0]);
        assertEquals(2, deliveryCalls[0]);
        assertTrue(barrier.acknowledge(playerB, context.absoluteDay()));
        pending.remove(playerB);
        assertTrue(pending.isEmpty());
        assertFalse(barrier.isLocked(playerB));
    }

    @Test
    void lateLockAfterBarrierPublicationReceivesItsOwnReadyResult() throws Exception {
        UUID participant = UUID.randomUUID();
        UUID lateLogin = UUID.randomUUID();
        DailySettlementContext context = new DailySettlementContext(
                226, 3, 0, 2, 1560, false, List.of(participant), Set.of());
        DailySettlementBarrier barrier = new DailySettlementBarrier();
        barrier.lockAll(226, List.of(participant));
        Map<UUID, OvernightSettlementPayload> sent = new HashMap<>();
        DailySettlementReadyPublisher publisher = new DailySettlementReadyPublisher(barrier,
                new DailySettlementReadyPublisher.Operations() {
                    public void prepareHooks(DailySettlementContext target) {}
                    public DailySettlementBarrier.ReadyResult prepareResult(DailySettlementContext target, UUID id) {
                        return ready(226);
                    }
                    public boolean isOnline(UUID id) { return true; }
                    public void send(UUID id, OvernightSettlementPayload payload) { sent.put(id, payload); }
                    public void wake(UUID id) {}
                });
        DailySettlementWorkUnit work = publisher.readyWork(context);
        while (barrier.readyResult(participant, 226) == null) {
            work.runNext();
        }
        barrier.lockAll(226, List.of(lateLogin));
        while (!work.isComplete()) {
            work.runNext();
        }
        assertNotNull(barrier.readyResult(lateLogin, 226));
        assertFalse(sent.get(lateLogin).personalSettlement());
    }

    @Test
    void offlineReconnectAcknowledgedBeforeDeliveryDoesNotRecreateSettlement() throws Exception {
        UUID id = UUID.randomUUID();
        DailySettlementContext context = new DailySettlementContext(
                226, 3, 0, 2, 1560, false, List.of(id), Set.of());
        DailySettlementBarrier barrier = new DailySettlementBarrier();
        barrier.lockAll(226, List.of(id));
        boolean[] online = {false};
        int[] preparations = {0};
        DailySettlementReadyPublisher publisher = new DailySettlementReadyPublisher(barrier,
                new DailySettlementReadyPublisher.Operations() {
                    public void prepareHooks(DailySettlementContext target) {}
                    public DailySettlementBarrier.ReadyResult prepareResult(DailySettlementContext target, UUID playerId) {
                        preparations[0]++;
                        return ready(226);
                    }
                    public boolean isOnline(UUID playerId) { return online[0]; }
                    public void send(UUID playerId, OvernightSettlementPayload payload) {}
                    public void wake(UUID playerId) {}
                });
        DailySettlementWorkUnit work = publisher.readyWork(context);
        while (barrier.readyResult(id, 226) == null) work.runNext();
        assertFalse(work.isComplete()); // Capture the delivery before the login/ACK tick.
        online[0] = true;
        assertTrue(barrier.replaceReady(id, ready(226)));
        assertTrue(barrier.acknowledge(id, 226));
        while (!work.isComplete()) work.runNext();
        assertEquals(1, preparations[0], "ACK must not recreate the completed personal settlement");
        assertFalse(barrier.isLocked(id));
    }

    @Test
    void offlineResultIsRefreshedWhenPlayerReturnsDuringReadyDelivery() throws Exception {
        UUID id = UUID.randomUUID();
        DailySettlementContext context = new DailySettlementContext(
                226, 3, 0, 2, 1560, false, List.of(id), Set.of());
        DailySettlementBarrier barrier = new DailySettlementBarrier();
        barrier.lockAll(226, List.of(id));
        boolean[] online = {false};
        int[] preparations = {0};
        List<OvernightSettlementPayload> sent = new ArrayList<>();
        DailySettlementReadyPublisher publisher = new DailySettlementReadyPublisher(barrier,
                new DailySettlementReadyPublisher.Operations() {
                    public void prepareHooks(DailySettlementContext target) {}
                    public DailySettlementBarrier.ReadyResult prepareResult(DailySettlementContext target, UUID playerId) {
                        preparations[0]++;
                        return ready(226);
                    }
                    public boolean isOnline(UUID playerId) { return online[0]; }
                    public void send(UUID playerId, OvernightSettlementPayload payload) { sent.add(payload); }
                    public void wake(UUID playerId) {}
                });
        DailySettlementWorkUnit work = publisher.readyWork(context);
        while (barrier.readyResult(id, 226) == null) {
            work.runNext();
        }
        OvernightSettlementPayload offline = barrier.readyResult(id, 226).payload();
        online[0] = true;
        while (!work.isComplete()) {
            work.runNext();
        }
        assertEquals(2, preparations[0]);
        assertTrue(offline != sent.getFirst(), "an offline placeholder must not be sent after login");
        assertTrue(sent.getFirst() == barrier.readyResult(id, 226).payload());
    }

    @Test
    void dynamicallyLockedLateLoginReceivesBarrierOnlyReadyWithoutPersonalSettlement() {
        UUID participant = UUID.randomUUID();
        UUID lateLogin = UUID.randomUUID();
        DailySettlementContext context = new DailySettlementContext(
                226, 3, 0, 2, 1560, false, List.of(participant), Set.of());
        DailySettlementBarrier barrier = new DailySettlementBarrier();
        barrier.lockAll(context.absoluteDay(), List.of(participant, lateLogin));
        Map<UUID, Integer> settlements = new HashMap<>();
        Map<UUID, OvernightSettlementPayload> sent = new HashMap<>();

        DailySettlementReadyPublisher publisher = new DailySettlementReadyPublisher(
                barrier, new DailySettlementReadyPublisher.Operations() {
                    @Override
                    public void prepareHooks(DailySettlementContext target) {
                    }

                    @Override
                    public DailySettlementBarrier.ReadyResult prepareResult(
                            DailySettlementContext target, UUID playerId) {
                        settlements.merge(playerId, 1, Integer::sum);
                        return ready(target.absoluteDay());
                    }

                    @Override
                    public boolean isOnline(UUID playerId) {
                        return true;
                    }

                    @Override
                    public void send(UUID playerId, OvernightSettlementPayload payload) {
                        sent.put(playerId, payload);
                    }

                    @Override
                    public void wake(UUID playerId) {
                    }
                });

        publisher.ready(context);

        assertEquals(Map.of(participant, 1), settlements);
        assertEquals(Set.of(participant, lateLogin), sent.keySet());
        assertTrue(barrier.readyResult(participant, 226).personalSettlement());
        assertFalse(barrier.readyResult(lateLogin, 226).personalSettlement());
        assertTrue(sent.get(lateLogin).shippedItems().isEmpty());
        assertTrue(sent.get(lateLogin).levelUps().isEmpty());
        assertFalse(sent.get(lateLogin).hasPassOut());
    }

    @Test
    void readySendsOnlyTheAuthoritativeSettlementPayload() {
        UUID playerId = UUID.randomUUID();
        DailySettlementContext context = new DailySettlementContext(
                226, 3, 0, 2, 1560, false, List.of(playerId), Set.of());
        DailySettlementBarrier barrier = new DailySettlementBarrier();
        barrier.lockAll(context.absoluteDay(), context.playerIds());
        List<String> deliveries = new ArrayList<>();

        DailySettlementReadyPublisher publisher = new DailySettlementReadyPublisher(
                barrier, new DailySettlementReadyPublisher.Operations() {
                    @Override
                    public void prepareHooks(DailySettlementContext target) {
                    }

                    @Override
                    public DailySettlementBarrier.ReadyResult prepareResult(
                            DailySettlementContext target, UUID targetPlayerId) {
                        return ready(target.absoluteDay());
                    }

                    @Override
                    public boolean isOnline(UUID targetPlayerId) {
                        return true;
                    }

                    @Override
                    public void send(
                            UUID targetPlayerId, OvernightSettlementPayload payload) {
                        deliveries.add("settlement:" + payload.absoluteDay());
                    }

                    @Override
                    public void wake(UUID targetPlayerId) {
                    }
                });

        publisher.ready(context);

        assertEquals(List.of("settlement:226"), deliveries);
    }

    private static DailySettlementBarrier.ReadyResult ready(int absoluteDay) {
        return new DailySettlementBarrier.ReadyResult(
                absoluteDay,
                new OvernightSettlementPayload(
                        absoluteDay, List.of(), List.of(), -1, 0, List.of()));
    }
}
