package com.stardew.craft.event;

import net.minecraft.world.level.ChunkPos;

import java.util.Objects;

/** Owns at most one temporary forced-chunk ticket and polls readiness without blocking. */
public final class FixedChunkLease<L> {
    public interface Backend<L> {
        boolean isForced(L level, ChunkPos chunk);

        boolean acquire(L level, ChunkPos chunk);

        boolean isLoaded(L level, ChunkPos chunk);

        void release(L level, ChunkPos chunk);
    }

    private final Backend<L> backend;
    private L level;
    private ChunkPos chunk;
    private boolean owned;

    public FixedChunkLease(Backend<L> backend) {
        this.backend = Objects.requireNonNull(backend, "backend");
    }

    public boolean request(L requestedLevel, ChunkPos requestedChunk) {
        Objects.requireNonNull(requestedLevel, "requestedLevel");
        Objects.requireNonNull(requestedChunk, "requestedChunk");
        if (level != requestedLevel || !requestedChunk.equals(chunk)) {
            release();
            level = requestedLevel;
            chunk = requestedChunk;
        }
        if (!backend.isForced(level, chunk) && backend.acquire(level, chunk)) {
            owned = true;
        }
        return backend.isForced(level, chunk) && backend.isLoaded(level, chunk);
    }

    public void release() {
        if (owned && level != null && chunk != null) {
            backend.release(level, chunk);
        }
        owned = false;
        level = null;
        chunk = null;
    }
}
