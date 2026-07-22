package com.stardew.craft.farm;

import org.junit.jupiter.api.Test;

import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FarmOccupancyTrackerTest {

    private final FarmOccupancyTracker<String> tracker = new FarmOccupancyTracker<>();

    @Test
    void firstEnterTracksPlayerAndSlotCount() {
        FarmOccupancyTracker.Transition transition = tracker.enter("player-a", 4);

        assertTrue(transition.changed());
        assertEquals(4, transition.slot());
        assertEquals(1, transition.count());
        assertEquals(OptionalInt.of(4), tracker.slotOf("player-a"));
        assertEquals(1, tracker.count(4));
        assertTrue(tracker.isOccupied(4));
    }

    @Test
    void duplicateEnterForSameSlotIsIdempotent() {
        tracker.enter("player-a", 4);

        FarmOccupancyTracker.Transition transition = tracker.enter("player-a", 4);

        assertFalse(transition.changed());
        assertEquals(4, transition.slot());
        assertEquals(1, transition.count());
        assertEquals(1, tracker.count(4));
    }

    @Test
    void switchingSlotsBalancesOldAndNewCounts() {
        tracker.enter("player-a", 4);

        FarmOccupancyTracker.Transition transition = tracker.enter("player-a", 9);

        assertTrue(transition.changed());
        assertEquals(9, transition.slot());
        assertEquals(1, transition.count());
        assertEquals(0, tracker.count(4));
        assertFalse(tracker.isOccupied(4));
        assertEquals(1, tracker.count(9));
        assertEquals(OptionalInt.of(9), tracker.slotOf("player-a"));
    }

    @Test
    void unknownLeaveIsNoOp() {
        assertTrue(tracker.leave("unknown").isEmpty());
        assertEquals(0, tracker.count(2));
    }

    @Test
    void doubleLeaveIsNoOpAfterFirstTransition() {
        tracker.enter("player-a", 4);

        FarmOccupancyTracker.Transition transition = tracker.leave("player-a").orElseThrow();

        assertTrue(transition.changed());
        assertEquals(4, transition.slot());
        assertEquals(0, transition.count());
        assertTrue(tracker.leave("player-a").isEmpty());
        assertEquals(0, tracker.count(4));
    }

    @Test
    void twoPlayersInSameSlotAreCountedIndependently() {
        tracker.enter("player-a", 4);
        tracker.enter("player-b", 4);

        FarmOccupancyTracker.Transition transition = tracker.leave("player-a").orElseThrow();

        assertEquals(1, transition.count());
        assertEquals(1, tracker.count(4));
        assertTrue(tracker.isOccupied(4));
        assertEquals(OptionalInt.of(4), tracker.slotOf("player-b"));
    }

    @Test
    void clearRemovesPlayersAndCounts() {
        tracker.enter("player-a", 4);
        tracker.enter("player-b", 9);

        tracker.clear();

        assertTrue(tracker.slotOf("player-a").isEmpty());
        assertTrue(tracker.slotOf("player-b").isEmpty());
        assertEquals(0, tracker.count(4));
        assertEquals(0, tracker.count(9));
    }

    @Test
    void rejectsNullPlayers() {
        assertThrows(NullPointerException.class, () -> tracker.enter(null, 1));
        assertThrows(NullPointerException.class, () -> tracker.leave(null));
        assertThrows(NullPointerException.class, () -> tracker.slotOf(null));
    }

    @Test
    void rejectsNegativeSlots() {
        assertThrows(IllegalArgumentException.class, () -> tracker.enter("player-a", -1));
        assertThrows(IllegalArgumentException.class, () -> tracker.count(-1));
        assertThrows(IllegalArgumentException.class, () -> tracker.isOccupied(-1));
    }
}
