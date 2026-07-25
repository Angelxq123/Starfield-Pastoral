package com.stardew.craft.farm;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FarmDebrisCursorTest {

    @Test
    void yieldsOneColumnThenOneAttemptPerStepInStablePhaseOrder() {
        FarmDebrisCursor cursor = new FarmDebrisCursor(10, 11, 20, 21, 2, 1, 1);
        List<String> steps = new ArrayList<>();

        while (!cursor.isComplete()) {
            steps.add(cursor.current().identity());
            cursor.advance();
        }

        assertEquals(List.of(
                "scan:10:20", "scan:10:21", "scan:11:20", "scan:11:21",
                "spread:0", "spread:1", "random:0", "spring_random:0",
                "spring_weeds:0"), steps);
    }

    @Test
    void skipSpreadJumpsDirectlyToRandomGeneration() {
        FarmDebrisCursor cursor = new FarmDebrisCursor(0, 0, 0, 0, 3, 2, 0);
        cursor.advance();

        assertEquals(FarmDebrisCursor.Phase.SPREAD, cursor.current().phase());
        cursor.skipSpread();

        assertEquals(FarmDebrisCursor.Phase.RANDOM, cursor.current().phase());
        assertEquals("random:0", cursor.current().identity());
    }

    @Test
    void emptyBoundsAndZeroAttemptsCompleteWithoutWork() {
        FarmDebrisCursor cursor = new FarmDebrisCursor(2, 1, 0, 0, 0, 0, 0);

        assertTrue(cursor.isComplete());
        assertFalse(cursor.hasCurrent());
    }
}
