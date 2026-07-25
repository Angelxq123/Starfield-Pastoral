package com.stardew.craft.time.settlement;

import com.stardew.craft.network.overnight.OvernightSettlementPayload;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
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
                    public boolean isOnline(UUID playerId) {
                        return true;
                    }

                    @Override
                    public void send(
                            UUID playerId, OvernightSettlementPayload payload) {
                        sends.merge(playerId, 1, Integer::sum);
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
        assertTrue(barrier.acknowledge(playerB, context.absoluteDay()));
        pending.remove(playerB);
        assertTrue(pending.isEmpty());
        assertFalse(barrier.isLocked(playerB));
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

    private static DailySettlementBarrier.ReadyResult ready(int absoluteDay) {
        return new DailySettlementBarrier.ReadyResult(
                absoluteDay,
                new OvernightSettlementPayload(
                        absoluteDay, List.of(), List.of(), -1, 0, List.of()));
    }
}
