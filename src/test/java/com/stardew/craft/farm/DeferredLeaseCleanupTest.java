package com.stardew.craft.farm;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeferredLeaseCleanupTest {

    @Test
    void releaseFailureDoesNotReplaySuccessfulBusinessOperation() {
        DeferredLeaseCleanup cleanup = new DeferredLeaseCleanup();
        AtomicInteger operationCalls = new AtomicInteger();
        AtomicInteger closeCalls = new AtomicInteger();
        TemporaryChunkLeaseTracker.Lease lease = () -> {
            if (closeCalls.incrementAndGet() == 1) {
                throw new IllegalStateException("release failed");
            }
        };

        cleanup.run(lease, operationCalls::incrementAndGet);

        assertEquals(1, operationCalls.get());
        assertEquals(1, cleanup.pendingCount());
        assertTrue(cleanup.retryOne());
        assertEquals(1, operationCalls.get());
        assertEquals(2, closeCalls.get());
        assertEquals(0, cleanup.pendingCount());
    }

    @Test
    void failedRetryRemainsQueued() {
        DeferredLeaseCleanup cleanup = new DeferredLeaseCleanup();
        cleanup.run(() -> { throw new AssertionError("still forced"); }, () -> {});

        assertFalse(cleanup.retryOne());
        assertEquals(1, cleanup.pendingCount());
    }
}
