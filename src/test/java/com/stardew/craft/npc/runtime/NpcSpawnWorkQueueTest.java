package com.stardew.craft.npc.runtime;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NpcSpawnWorkQueueTest {

    @Test
    void scheduledChecksAreDrainedInBoundedBatches() {
        NpcSpawnWorkQueue queue = new NpcSpawnWorkQueue();

        queue.enqueueScheduled(List.of("abigail", "lewis", "wizard"));

        assertEquals(List.of("abigail", "lewis"), queue.drain(2));
        assertEquals(1, queue.pendingCount());
        assertEquals(List.of("wizard"), queue.drain(2));
    }

    @Test
    void forcedNpcJumpsAheadWithoutDuplicatingScheduledWork() {
        NpcSpawnWorkQueue queue = new NpcSpawnWorkQueue();
        queue.enqueueScheduled(List.of("abigail", "wizard", "lewis"));

        queue.prioritize(List.of("wizard"));

        assertEquals(List.of("wizard", "abigail"), queue.drain(2));
        assertEquals(List.of("lewis"), queue.drain(2));
    }

    @Test
    void repeatedSchedulingDoesNotGrowTheQueue() {
        NpcSpawnWorkQueue queue = new NpcSpawnWorkQueue();

        queue.enqueueScheduled(List.of("abigail", "lewis"));
        queue.enqueueScheduled(List.of("abigail", "lewis"));
        queue.prioritize(List.of("lewis", "lewis"));

        assertEquals(2, queue.pendingCount());
        assertEquals(List.of("lewis", "abigail"), queue.drain(10));
    }
}
