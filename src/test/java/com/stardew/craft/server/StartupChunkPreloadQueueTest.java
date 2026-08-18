package com.stardew.craft.server;

import com.stardew.craft.event.StartupChunkPreloadQueue;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StartupChunkPreloadQueueTest {
    @Test
    void requestsAtMostTwoChunksPerTickAndRunsAfterAllAreReady() {
        FakeBackend backend = new FakeBackend();
        StartupChunkPreloadQueue<String> queue = new StartupChunkPreloadQueue<>(backend, 2, 2);
        AtomicInteger runs = new AtomicInteger();
        List<ChunkPos> chunks = List.of(
                new ChunkPos(0, 0), new ChunkPos(1, 0), new ChunkPos(2, 0));

        queue.enqueue("public", "valley", chunks, runs::incrementAndGet);
        queue.tick();
        assertEquals(2, backend.acquireCalls.size());
        queue.tick();
        assertEquals(3, backend.acquireCalls.size());
        assertEquals(0, runs.get());

        backend.loaded.addAll(chunks);
        queue.tick();
        assertEquals(1, runs.get());
        assertFalse(queue.isIdle());
        queue.tick();
        queue.tick();
        assertTrue(queue.isIdle());
    }

    @Test
    void releasesOnlyTicketsAcquiredByThisQueue() {
        FakeBackend backend = new FakeBackend();
        ChunkPos shared = new ChunkPos(4, 5);
        ChunkPos owned = new ChunkPos(6, 7);
        backend.preExisting.add(shared);
        backend.loaded.add(shared);
        backend.loaded.add(owned);
        StartupChunkPreloadQueue<String> queue = new StartupChunkPreloadQueue<>(backend, 2, 2);

        queue.enqueue("ownership", "valley", List.of(shared, owned), () -> {});
        tickUntilIdle(queue);

        assertEquals(List.of(owned), backend.releaseCalls);
    }

    @Test
    void failedWorkStillReleasesOwnedTicketsAndLetsNextJobRun() {
        FakeBackend backend = new FakeBackend();
        ChunkPos first = new ChunkPos(1, 1);
        ChunkPos second = new ChunkPos(2, 2);
        backend.loaded.add(first);
        backend.loaded.add(second);
        StartupChunkPreloadQueue<String> queue = new StartupChunkPreloadQueue<>(backend, 2, 2);
        AtomicInteger runs = new AtomicInteger();

        queue.enqueue("broken", "valley", List.of(first), () -> {
            throw new IllegalStateException("boom");
        });
        queue.enqueue("next", "valley", List.of(second), runs::incrementAndGet);
        tickUntilIdle(queue);

        assertEquals(1, backend.failures);
        assertEquals(1, runs.get());
        assertEquals(Set.of(first, second), Set.copyOf(backend.releaseCalls));
    }

    @Test
    void reacquiresATicketLostWhileWaitingForChunkReadiness() {
        FakeBackend backend = new FakeBackend();
        ChunkPos chunk = new ChunkPos(11, 12);
        StartupChunkPreloadQueue<String> queue = new StartupChunkPreloadQueue<>(backend, 2, 2);
        AtomicInteger runs = new AtomicInteger();

        queue.enqueue("reacquire", "valley", List.of(chunk), runs::incrementAndGet);
        queue.tick();
        backend.forced.remove(chunk);
        queue.tick();

        assertEquals(2, backend.acquireCalls.size());
        backend.loaded.add(chunk);
        queue.tick();
        queue.tick();
        assertEquals(1, runs.get());
        assertTrue(queue.isIdle());
    }

    private static void tickUntilIdle(StartupChunkPreloadQueue<?> queue) {
        for (int i = 0; i < 30 && !queue.isIdle(); i++) {
            queue.tick();
        }
        assertTrue(queue.isIdle());
    }

    private static final class FakeBackend implements StartupChunkPreloadQueue.Backend<String> {
        final Set<ChunkPos> preExisting = new HashSet<>();
        final Set<ChunkPos> forced = new HashSet<>();
        final Set<ChunkPos> loaded = new HashSet<>();
        final List<ChunkPos> acquireCalls = new ArrayList<>();
        final List<ChunkPos> releaseCalls = new ArrayList<>();
        int failures;

        @Override
        public boolean isForced(String level, ChunkPos chunk) {
            return preExisting.contains(chunk) || forced.contains(chunk);
        }

        @Override
        public boolean acquire(String level, ChunkPos chunk) {
            acquireCalls.add(chunk);
            if (preExisting.contains(chunk)) {
                return false;
            }
            forced.add(chunk);
            return true;
        }

        @Override
        public boolean isLoaded(String level, ChunkPos chunk) {
            return loaded.contains(chunk);
        }

        @Override
        public void release(String level, ChunkPos chunk) {
            releaseCalls.add(chunk);
            forced.remove(chunk);
        }

        @Override
        public void onFailure(String id, RuntimeException exception) {
            failures++;
        }
    }
}
