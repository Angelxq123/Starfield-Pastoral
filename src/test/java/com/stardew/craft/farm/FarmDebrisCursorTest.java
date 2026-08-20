package com.stardew.craft.farm;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FarmDebrisCursorTest {

    @Test
    void yieldsOneChunkThenOneAttemptPerStepInStablePhaseOrder() {
        FarmDebrisCursor cursor = new FarmDebrisCursor(10, 11, 20, 21, 2, 1, 1);
        List<String> steps = new ArrayList<>();

        while (!cursor.isComplete()) {
            steps.add(cursor.current().identity());
            cursor.advance();
        }

        assertEquals(List.of(
                "scan:10:20",
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

    @Test
    void scansEachChunkInOneContiguousRun() {
        FarmDebrisCursor cursor = new FarmDebrisCursor(0, 17, 0, 17, 0, 0, 0);
        List<Long> chunkRuns = new ArrayList<>();
        long previous = Long.MIN_VALUE;

        while (!cursor.isComplete()) {
            FarmDebrisCursor.Step step = cursor.current();
            if (step.phase() != FarmDebrisCursor.Phase.SCAN) {
                break;
            }
            long chunk = net.minecraft.world.level.ChunkPos.asLong(
                    step.x() >> 4, step.z() >> 4);
            if (chunk != previous) {
                chunkRuns.add(chunk);
                previous = chunk;
            }
            cursor.advance();
        }

        assertEquals(4, chunkRuns.size());
        assertEquals(4, new LinkedHashSet<>(chunkRuns).size(),
                "a scan must never return to a chunk after releasing its lease");
    }
}
