package com.stardew.craft.server;

import com.stardew.craft.event.FixedChunkLease;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FixedChunkLeaseTest {
    @Test
    void waitsForAsyncReadinessAndReleasesOwnedTicket() {
        FakeBackend backend = new FakeBackend();
        FixedChunkLease<String> lease = new FixedChunkLease<>(backend);
        ChunkPos chunk = new ChunkPos(3, 4);

        assertFalse(lease.request("valley", chunk));
        assertEquals(1, backend.acquires);
        backend.loaded = true;
        assertTrue(lease.request("valley", chunk));

        lease.release();
        assertEquals(1, backend.releases);
    }

    @Test
    void doesNotReleaseAChunkForcedByAnotherOwner() {
        FakeBackend backend = new FakeBackend();
        backend.forced = true;
        backend.loaded = true;
        FixedChunkLease<String> lease = new FixedChunkLease<>(backend);

        assertTrue(lease.request("valley", new ChunkPos(1, 2)));
        lease.release();

        assertEquals(0, backend.acquires);
        assertEquals(0, backend.releases);
    }

    @Test
    void reacquiresWhenAnExternalTicketDisappears() {
        FakeBackend backend = new FakeBackend();
        backend.forced = true;
        backend.loaded = true;
        FixedChunkLease<String> lease = new FixedChunkLease<>(backend);
        ChunkPos chunk = new ChunkPos(8, 9);
        assertTrue(lease.request("valley", chunk));

        backend.forced = false;
        assertTrue(lease.request("valley", chunk));
        assertEquals(1, backend.acquires);
        lease.release();
        assertEquals(1, backend.releases);
    }

    private static final class FakeBackend implements FixedChunkLease.Backend<String> {
        boolean forced;
        boolean loaded;
        int acquires;
        int releases;

        @Override
        public boolean isForced(String level, ChunkPos chunk) {
            return forced;
        }

        @Override
        public boolean acquire(String level, ChunkPos chunk) {
            acquires++;
            forced = true;
            return true;
        }

        @Override
        public boolean isLoaded(String level, ChunkPos chunk) {
            return loaded;
        }

        @Override
        public void release(String level, ChunkPos chunk) {
            releases++;
            forced = false;
        }
    }
}
