package com.stardew.craft.interior;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerInteriorReloadQueueTest {

    private static final UUID A = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID B = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Test
    void schedulesOneWorkItemPerPlacedInterior() {
        PlayerInteriorReloadQueue queue = new PlayerInteriorReloadQueue();

        queue.reset(List.of(B, A), List.of(A), List.of(B));

        assertEquals(
                List.of(
                        new PlayerInteriorReloadQueue.Work(
                                A, PlayerInteriorReloadQueue.Kind.COMMUNITY_CENTER),
                        new PlayerInteriorReloadQueue.Work(
                                B, PlayerInteriorReloadQueue.Kind.COMMUNITY_CENTER),
                        new PlayerInteriorReloadQueue.Work(
                                A, PlayerInteriorReloadQueue.Kind.GREENHOUSE),
                        new PlayerInteriorReloadQueue.Work(
                                B, PlayerInteriorReloadQueue.Kind.FARM_CAVE)),
                List.of(queue.poll(), queue.poll(), queue.poll(), queue.poll()));
        assertTrue(queue.isEmpty());
    }

    @Test
    void resetReplacesAnInterruptedReloadPlan() {
        PlayerInteriorReloadQueue queue = new PlayerInteriorReloadQueue();
        queue.reset(List.of(A), List.of(A), List.of(A));
        queue.poll();

        queue.reset(List.of(B), List.of(), List.of());

        assertEquals(
                new PlayerInteriorReloadQueue.Work(
                        B, PlayerInteriorReloadQueue.Kind.COMMUNITY_CENTER),
                queue.poll());
        assertTrue(queue.isEmpty());
    }

    @Test
    void peekingDoesNotLoseWorkUntilSuccessfulRemoval() {
        PlayerInteriorReloadQueue queue = new PlayerInteriorReloadQueue();
        queue.reset(List.of(A), List.of(), List.of());

        PlayerInteriorReloadQueue.Work work = queue.peek();

        assertEquals(work, queue.peek());
        assertEquals(1, queue.size());
        queue.removeFirst();
        assertTrue(queue.isEmpty());
    }
}
