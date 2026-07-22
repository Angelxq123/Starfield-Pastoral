package com.stardew.craft.farm;

import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TemporaryChunkLeaseTrackerTest {

    private static final ChunkPos A = new ChunkPos(1, 2);
    private static final ChunkPos B = new ChunkPos(3, 4);
    private static final ChunkPos C = new ChunkPos(5, 6);
    private static final ChunkPos D = new ChunkPos(7, 8);

    @Test
    void rejectsNullDependenciesAndRequests() {
        assertThrows(NullPointerException.class, () -> new TemporaryChunkLeaseTracker<>(null));

        RecordingBackend backend = new RecordingBackend();
        TemporaryChunkLeaseTracker<TestLevel> tracker = new TemporaryChunkLeaseTracker<>(backend);
        TestLevel level = new TestLevel("level");

        assertThrows(NullPointerException.class, () -> tracker.acquire(null, List.of(A)));
        assertThrows(NullPointerException.class, () -> tracker.acquire(level, null));
        assertThrows(NullPointerException.class, () -> tracker.acquire(level, collectionWithNull()));
    }

    @Test
    void emptyRequestReturnsNoOpLease() {
        RecordingBackend backend = new RecordingBackend();
        TemporaryChunkLeaseTracker<TestLevel> tracker = new TemporaryChunkLeaseTracker<>(backend);

        TemporaryChunkLeaseTracker.Lease lease = tracker.acquire(new TestLevel("level"), List.of());
        lease.close();
        lease.close();

        assertEquals(List.of(), backend.acquires);
        assertEquals(List.of(), backend.releases);
    }

    @Test
    void duplicateChunksAreAcquiredAndReleasedOnce() {
        RecordingBackend backend = new RecordingBackend();
        TemporaryChunkLeaseTracker<TestLevel> tracker = new TemporaryChunkLeaseTracker<>(backend);
        TestLevel level = new TestLevel("level");

        tracker.acquire(level, List.of(A, A, B, A, B)).close();

        assertEquals(List.of(A, B), backend.acquiredChunks());
        assertEquals(List.of(A, B), backend.releasedChunks());
    }

    @Test
    void overlappingLeasesShareEntriesUntilFinalClose() {
        RecordingBackend backend = new RecordingBackend();
        TemporaryChunkLeaseTracker<TestLevel> tracker = new TemporaryChunkLeaseTracker<>(backend);
        TestLevel level = new TestLevel("level");

        TemporaryChunkLeaseTracker.Lease first = tracker.acquire(level, List.of(A, B));
        TemporaryChunkLeaseTracker.Lease second = tracker.acquire(level, List.of(B, C));

        assertEquals(List.of(A, B, C), backend.acquiredChunks());
        first.close();
        assertEquals(List.of(A), backend.releasedChunks());
        second.close();
        assertEquals(List.of(A, B, C), backend.releasedChunks());
    }

    @Test
    void unownedChunksAreNeverReleased() {
        RecordingBackend backend = new RecordingBackend();
        backend.unowned.add(B);
        TemporaryChunkLeaseTracker<TestLevel> tracker = new TemporaryChunkLeaseTracker<>(backend);

        tracker.acquire(new TestLevel("level"), List.of(A, B)).close();

        assertEquals(List.of(A, B), backend.acquiredChunks());
        assertEquals(List.of(A), backend.releasedChunks());
    }

    @Test
    void equalLevelsAreTrackedByObjectIdentity() {
        RecordingBackend backend = new RecordingBackend();
        TemporaryChunkLeaseTracker<TestLevel> tracker = new TemporaryChunkLeaseTracker<>(backend);
        TestLevel firstLevel = new TestLevel("same");
        TestLevel secondLevel = new TestLevel("same");

        TemporaryChunkLeaseTracker.Lease first = tracker.acquire(firstLevel, List.of(A));
        TemporaryChunkLeaseTracker.Lease second = tracker.acquire(secondLevel, List.of(A));
        first.close();
        second.close();

        assertEquals(2, backend.acquires.size());
        assertSame(firstLevel, backend.acquires.get(0).level());
        assertSame(secondLevel, backend.acquires.get(1).level());
        assertSame(firstLevel, backend.releases.get(0).level());
        assertSame(secondLevel, backend.releases.get(1).level());
    }

    @Test
    void closingLeaseTwiceIsIdempotent() {
        RecordingBackend backend = new RecordingBackend();
        TemporaryChunkLeaseTracker<TestLevel> tracker = new TemporaryChunkLeaseTracker<>(backend);

        TemporaryChunkLeaseTracker.Lease lease = tracker.acquire(new TestLevel("level"), List.of(A));
        lease.close();
        lease.close();

        assertEquals(List.of(A), backend.releasedChunks());
    }

    @Test
    void failedAcquireRollsBackMixedEntriesInReverseOrder() {
        RecordingBackend backend = new RecordingBackend();
        backend.failOn = D;
        TemporaryChunkLeaseTracker<TestLevel> tracker = new TemporaryChunkLeaseTracker<>(backend);
        TestLevel level = new TestLevel("level");
        TemporaryChunkLeaseTracker.Lease existing = tracker.acquire(level, List.of(A));

        RuntimeException failure = assertThrows(RuntimeException.class,
            () -> tracker.acquire(level, List.of(A, B, C, D)));

        assertEquals("failed 7,8", failure.getMessage());
        assertEquals(List.of(C, B), backend.releasedChunks());

        backend.failOn = null;
        tracker.acquire(level, List.of(B)).close();
        assertEquals(2, backend.acquireCount(B));

        existing.close();
        assertEquals(List.of(C, B, B, A), backend.releasedChunks());
    }

    @Test
    void rollbackDoesNotReleaseNewUnownedEntries() {
        RecordingBackend backend = new RecordingBackend();
        backend.unowned.add(B);
        backend.failOn = C;
        TemporaryChunkLeaseTracker<TestLevel> tracker = new TemporaryChunkLeaseTracker<>(backend);

        assertThrows(RuntimeException.class,
            () -> tracker.acquire(new TestLevel("level"), List.of(A, B, C)));

        assertEquals(List.of(A), backend.releasedChunks());
    }

    @Test
    void closeAllForLevelLeavesOtherEqualLevelActive() {
        RecordingBackend backend = new RecordingBackend();
        TemporaryChunkLeaseTracker<TestLevel> tracker = new TemporaryChunkLeaseTracker<>(backend);
        TestLevel firstLevel = new TestLevel("same");
        TestLevel secondLevel = new TestLevel("same");
        TemporaryChunkLeaseTracker.Lease first = tracker.acquire(firstLevel, List.of(A));
        TemporaryChunkLeaseTracker.Lease second = tracker.acquire(secondLevel, List.of(B));

        tracker.closeAll(firstLevel);

        assertEquals(1, backend.releases.size());
        assertSame(firstLevel, backend.releases.get(0).level());
        first.close();
        assertEquals(1, backend.releases.size());
        second.close();
        assertSame(secondLevel, backend.releases.get(1).level());
    }

    @Test
    void staleHandleCannotTouchReacquiredEntryAfterCloseAll() {
        RecordingBackend backend = new RecordingBackend();
        TemporaryChunkLeaseTracker<TestLevel> tracker = new TemporaryChunkLeaseTracker<>(backend);
        TestLevel level = new TestLevel("level");
        TemporaryChunkLeaseTracker.Lease stale = tracker.acquire(level, List.of(A));

        tracker.closeAll(level);
        TemporaryChunkLeaseTracker.Lease current = tracker.acquire(level, List.of(A));
        stale.close();

        assertEquals(1, backend.releases.size());
        current.close();
        assertEquals(2, backend.releases.size());
        assertEquals(2, backend.acquireCount(A));
    }

    @Test
    void closeAllReleasesEveryOwnedEntryOnceAndInvalidatesHandles() {
        RecordingBackend backend = new RecordingBackend();
        backend.unowned.add(B);
        TemporaryChunkLeaseTracker<TestLevel> tracker = new TemporaryChunkLeaseTracker<>(backend);
        TemporaryChunkLeaseTracker.Lease first = tracker.acquire(new TestLevel("first"), List.of(A, B));
        TemporaryChunkLeaseTracker.Lease second = tracker.acquire(new TestLevel("second"), List.of(C));

        tracker.closeAll();
        first.close();
        second.close();
        tracker.closeAll();

        assertEquals(Set.of(A, C), new HashSet<>(backend.releasedChunks()));
        assertEquals(2, backend.releases.size());
    }

    private static Collection<ChunkPos> collectionWithNull() {
        List<ChunkPos> chunks = new ArrayList<>();
        chunks.add(A);
        chunks.add(null);
        return chunks;
    }

    private static final class TestLevel {
        private final String key;

        private TestLevel(String key) {
            this.key = key;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof TestLevel level && key.equals(level.key);
        }

        @Override
        public int hashCode() {
            return key.hashCode();
        }
    }

    private record Call(TestLevel level, ChunkPos chunk) {}

    private static final class RecordingBackend implements TemporaryChunkLeaseTracker.Backend<TestLevel> {
        private final List<Call> acquires = new ArrayList<>();
        private final List<Call> releases = new ArrayList<>();
        private final Set<ChunkPos> unowned = new HashSet<>();
        private ChunkPos failOn;

        @Override
        public boolean acquire(TestLevel level, ChunkPos chunk) {
            acquires.add(new Call(level, chunk));
            if (chunk.equals(failOn)) {
                throw new RuntimeException("failed " + chunk.x + "," + chunk.z);
            }
            return !unowned.contains(chunk);
        }

        @Override
        public void release(TestLevel level, ChunkPos chunk) {
            releases.add(new Call(level, chunk));
        }

        private List<ChunkPos> acquiredChunks() {
            return acquires.stream().map(Call::chunk).toList();
        }

        private List<ChunkPos> releasedChunks() {
            return releases.stream().map(Call::chunk).toList();
        }

        private long acquireCount(ChunkPos chunk) {
            return acquiredChunks().stream().filter(chunk::equals).count();
        }
    }
}
