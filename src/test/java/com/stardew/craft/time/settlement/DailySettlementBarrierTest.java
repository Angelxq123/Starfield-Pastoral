package com.stardew.craft.time.settlement;

import com.stardew.craft.network.overnight.OvernightSettlementPayload;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DailySettlementBarrierTest {

    @Test
    void lockAllIsIdempotentForTheSameDay() {
        DailySettlementBarrier barrier = new DailySettlementBarrier();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        barrier.lockAll(42, List.of(first, second));
        barrier.lockAll(42, List.of(first));

        assertTrue(barrier.isLocked(first));
        assertTrue(barrier.isLocked(second));
        assertEquals(42, barrier.lockedDay(first));
        assertFalse(barrier.canCancelSleep(first));
    }

    @Test
    void lockAllRejectsInvalidAndConflictingDaysWithoutPartialMutation() {
        DailySettlementBarrier barrier = new DailySettlementBarrier();
        UUID existing = UUID.randomUUID();
        UUID newcomer = UUID.randomUUID();
        barrier.lockAll(7, List.of(existing));

        assertThrows(IllegalArgumentException.class, () -> barrier.lockAll(0, List.of(existing)));
        assertThrows(IllegalStateException.class,
                () -> barrier.lockAll(8, List.of(newcomer, existing)));

        assertFalse(barrier.isLocked(newcomer));
        assertEquals(7, barrier.lockedDay(existing));
    }

    @Test
    void lockAllDefensivelyValidatesTheWholePlayerCollection() {
        DailySettlementBarrier barrier = new DailySettlementBarrier();
        UUID valid = UUID.randomUUID();

        assertThrows(NullPointerException.class,
                () -> barrier.lockAll(3, java.util.Arrays.asList(valid, null)));

        assertFalse(barrier.isLocked(valid));
    }

    @Test
    void publishReadyRequiresTheMatchingLockAndKeepsItLocked() {
        DailySettlementBarrier barrier = new DailySettlementBarrier();
        UUID player = UUID.randomUUID();
        OvernightSettlementPayload payload = payload(15);
        DailySettlementBarrier.ReadyResult ready =
                new DailySettlementBarrier.ReadyResult(15, payload);
        barrier.lockAll(15, List.of(player));

        assertTrue(barrier.publishReady(player, ready));
        assertTrue(barrier.publishReady(player, ready));
        assertSame(ready, barrier.readyResult(player, 15));
        assertTrue(barrier.isLocked(player));
        assertFalse(barrier.canCancelSleep(player));
    }

    @Test
    void publishReadyRejectsMissingStaleAndConflictingResults() {
        DailySettlementBarrier barrier = new DailySettlementBarrier();
        UUID player = UUID.randomUUID();
        UUID unlocked = UUID.randomUUID();
        barrier.lockAll(20, List.of(player));
        DailySettlementBarrier.ReadyResult current =
                new DailySettlementBarrier.ReadyResult(20, payload(20));

        assertFalse(barrier.publishReady(player,
                new DailySettlementBarrier.ReadyResult(19, payload(19))));
        assertFalse(barrier.publishReady(unlocked, current));
        assertTrue(barrier.publishReady(player, current));
        assertFalse(barrier.publishReady(player,
                new DailySettlementBarrier.ReadyResult(20, payload(20))));

        assertSame(current, barrier.readyResult(player, 20));
        assertNull(barrier.readyResult(player, 19));
    }

    @Test
    void acknowledgeOnlyRemovesMatchingLockAndReadyResult() {
        DailySettlementBarrier barrier = new DailySettlementBarrier();
        UUID player = UUID.randomUUID();
        DailySettlementBarrier.ReadyResult ready =
                new DailySettlementBarrier.ReadyResult(31, payload(31));
        barrier.lockAll(31, List.of(player));
        barrier.publishReady(player, ready);

        assertFalse(barrier.acknowledge(player, 30));
        assertTrue(barrier.isLocked(player));
        assertSame(ready, barrier.readyResult(player, 31));

        assertTrue(barrier.acknowledge(player, 31));
        assertFalse(barrier.isLocked(player));
        assertTrue(barrier.canCancelSleep(player));
        assertNull(barrier.readyResult(player, 31));
        assertFalse(barrier.acknowledge(player, 31));
    }

    @Test
    void stateSurvivesDisconnectAndReconnectLookupByUuid() {
        DailySettlementBarrier barrier = new DailySettlementBarrier();
        UUID stablePlayerId = UUID.randomUUID();
        DailySettlementBarrier.ReadyResult ready =
                new DailySettlementBarrier.ReadyResult(54, payload(54));
        barrier.lockAll(54, List.of(stablePlayerId));
        barrier.publishReady(stablePlayerId, ready);

        // A disconnect performs no mutation; a new player object uses the same UUID.
        UUID reconnectId = UUID.fromString(stablePlayerId.toString());

        assertTrue(barrier.isLocked(reconnectId));
        assertEquals(54, barrier.lockedDay(reconnectId));
        assertSame(ready, barrier.readyResult(reconnectId, 54));
    }

    @Test
    void readyResultRequiresANonNullPayload() {
        assertThrows(NullPointerException.class,
                () -> new DailySettlementBarrier.ReadyResult(1, null));
    }

    @Test
    void readyResultRejectsNonPositiveWrapperDay() {
        assertThrows(IllegalArgumentException.class,
                () -> new DailySettlementBarrier.ReadyResult(0, payload(1)));
    }

    @Test
    void readyResultRejectsLegacyOrNonPositivePayloadDay() {
        OvernightSettlementPayload legacyPayload =
                new OvernightSettlementPayload(List.of(), List.of());

        assertThrows(IllegalArgumentException.class,
                () -> new DailySettlementBarrier.ReadyResult(15, legacyPayload));
        assertThrows(IllegalArgumentException.class,
                () -> new DailySettlementBarrier.ReadyResult(15, payload(0)));
    }

    @Test
    void readyResultRejectsMismatchedPayloadDay() {
        assertThrows(IllegalArgumentException.class,
                () -> new DailySettlementBarrier.ReadyResult(15, payload(16)));
    }

    @Test
    void matchingPositiveReadyDayPublishesAndAcknowledges() {
        DailySettlementBarrier barrier = new DailySettlementBarrier();
        UUID player = UUID.randomUUID();
        DailySettlementBarrier.ReadyResult ready =
                new DailySettlementBarrier.ReadyResult(73, payload(73));
        barrier.lockAll(73, List.of(player));

        assertTrue(barrier.publishReady(player, ready));
        assertSame(ready, barrier.readyResult(player, 73));
        assertTrue(barrier.acknowledge(player, 73));
        assertFalse(barrier.isLocked(player));
        assertNull(barrier.readyResult(player, 73));
    }

    private static OvernightSettlementPayload payload(int absoluteDay) {
        return new OvernightSettlementPayload(absoluteDay, List.of(), List.of());
    }
}
