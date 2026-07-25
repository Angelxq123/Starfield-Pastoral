package com.stardew.craft.farm;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OfflineFarmCatchUpCursorTest {

    @Test
    void advancesOneOperationAtATimeAndCommitsEveryCompletedDay() {
        List<String> events = new ArrayList<>();
        OfflineFarmCatchUpCursor<String> cursor = cursor(events, 4, 6);

        while (!cursor.isComplete()) {
            assertTrue(cursor.runNext());
        }

        assertEquals(List.of(
                "crop:5:a", "crop:5:b", "tree:5:t", "commit:5",
                "crop:6:a", "crop:6:b", "tree:6:t", "sprinkler:6:s", "commit:6"),
                events);
        assertFalse(cursor.runNext());
    }

    @Test
    void failedOperationKeepsItsCursorUntilRetrySucceeds() {
        List<String> events = new ArrayList<>();
        int[] attempts = {0};
        OfflineFarmCatchUpCursor<String> cursor = new OfflineFarmCatchUpCursor<>(
                8, 9, List.of("a"), List.of(), List.of(),
                new OfflineFarmCatchUpCursor.Operations<>() {
                    @Override
                    public void growCrop(int absoluteDay, String crop) {
                        attempts[0]++;
                        if (attempts[0] == 1) {
                            throw new IllegalStateException("retry");
                        }
                        events.add("crop:" + absoluteDay + ":" + crop);
                    }

                    @Override public void growTree(int absoluteDay, String tree) {}
                    @Override public void waterSprinkler(String sprinkler) {}
                    @Override public void commitDay(int absoluteDay) {
                        events.add("commit:" + absoluteDay);
                    }
                });

        assertThrows(IllegalStateException.class, cursor::runNext);
        assertTrue(cursor.runNext());
        assertTrue(cursor.runNext());

        assertEquals(2, attempts[0]);
        assertEquals(List.of("crop:9:a", "commit:9"), events);
        assertTrue(cursor.isComplete());
    }

    @Test
    void aFailedItemCanBeSkippedButACommitCannot() {
        OfflineFarmCatchUpCursor<String> cursor = new OfflineFarmCatchUpCursor<>(
                2, 3, List.of("a"), List.of("t"), List.of(),
                new OfflineFarmCatchUpCursor.Operations<>() {
                    @Override public void growCrop(int day, String crop) {}
                    @Override public void growTree(int day, String tree) {}
                    @Override public void waterSprinkler(String sprinkler) {}
                    @Override public void commitDay(int day) {}
                });

        assertTrue(cursor.canSkipFailedItem());
        cursor.skipFailedItem();
        cursor.skipFailedItem();
        assertFalse(cursor.canSkipFailedItem());
        assertThrows(IllegalStateException.class, cursor::skipFailedItem);
    }

    private static OfflineFarmCatchUpCursor<String> cursor(
            List<String> events, int completedDay, int targetDay) {
        return new OfflineFarmCatchUpCursor<>(
                completedDay, targetDay, List.of("a", "b"), List.of("t"), List.of("s"),
                new OfflineFarmCatchUpCursor.Operations<>() {
                    @Override public void growCrop(int day, String crop) {
                        events.add("crop:" + day + ":" + crop);
                    }

                    @Override public void growTree(int day, String tree) {
                        events.add("tree:" + day + ":" + tree);
                    }

                    @Override public void waterSprinkler(String sprinkler) {
                        events.add("sprinkler:" + targetDay + ":" + sprinkler);
                    }

                    @Override public void commitDay(int day) {
                        events.add("commit:" + day);
                    }
                });
    }
}
