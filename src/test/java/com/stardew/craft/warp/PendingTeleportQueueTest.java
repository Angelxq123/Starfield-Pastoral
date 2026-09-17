package com.stardew.craft.warp;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class PendingTeleportQueueTest {
    @Test void duplicatesTimeoutAndStaleRequestsReleaseExactlyOnce() {
        var queue = new PendingTeleportQueue<Job>(2, 100, 3, () -> 0L);
        UUID id = UUID.randomUUID();
        Job job = new Job();
        assertTrue(queue.enqueue(id, job, 0));
        assertFalse(queue.enqueue(id, new Job(), 1));
        queue.tick(2);
        assertEquals(0, job.closed);
        queue.tick(3);
        assertEquals(1, job.closed);
        assertEquals(0, job.completed);
        Job stale = new Job();
        stale.valid = false;
        queue.enqueue(id, stale, 4);
        queue.tick(4);
        assertEquals(1, stale.closed);
        queue.clear();
        assertEquals(1, stale.closed);
    }

    @Test void readyRequestsShareTheTimeBudgetAndColdRequestsDoNotStarveThem() {
        long[] clock = {0};
        var queue = new PendingTeleportQueue<Job>(8, 10, 200, () -> clock[0]);
        Job cold = new Job();
        queue.enqueue(UUID.randomUUID(), cold, 0);
        Job first = new Job(); first.ready = true; first.action = () -> clock[0] += 11;
        Job second = new Job(); second.ready = true;
        queue.enqueue(UUID.randomUUID(), first, 0);
        queue.enqueue(UUID.randomUUID(), second, 0);
        queue.tick(1);
        assertEquals(1, first.completed);
        assertEquals(0, second.completed);
        queue.tick(2);
        assertEquals(1, second.completed);
        assertEquals(1, first.closed);
        queue.clear();
        assertEquals(1, cold.closed);
    }

    private static final class Job implements PendingTeleportQueue.Request {
        boolean valid = true, ready;
        int completed, closed;
        Runnable action = () -> {};
        public boolean isValid() { return valid; }
        public boolean isReady() { return ready; }
        public void complete() { completed++; action.run(); }
        public void close() { closed++; }
    }
}
