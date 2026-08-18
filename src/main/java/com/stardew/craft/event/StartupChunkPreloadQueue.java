package com.stardew.craft.event;

import net.minecraft.world.level.ChunkPos;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;

/** Serializes temporary chunk warmups without performing synchronous chunk loads. */
public final class StartupChunkPreloadQueue<L> {
    public interface Backend<L> {
        boolean isForced(L level, ChunkPos chunk);

        boolean acquire(L level, ChunkPos chunk);

        boolean isLoaded(L level, ChunkPos chunk);

        void release(L level, ChunkPos chunk);

        void onFailure(String id, RuntimeException exception);
    }

    private final Backend<L> backend;
    private final int requestsPerTick;
    private final int releasesPerTick;
    private final Queue<Job<L>> pending = new ArrayDeque<>();
    private final Set<String> scheduledIds = new LinkedHashSet<>();
    private Job<L> active;

    public StartupChunkPreloadQueue(
            Backend<L> backend,
            int requestsPerTick,
            int releasesPerTick
    ) {
        this.backend = Objects.requireNonNull(backend, "backend");
        if (requestsPerTick <= 0 || releasesPerTick <= 0) {
            throw new IllegalArgumentException("chunk limits must be positive");
        }
        this.requestsPerTick = requestsPerTick;
        this.releasesPerTick = releasesPerTick;
    }

    public boolean enqueue(String id, L level, Collection<ChunkPos> chunks, Runnable work) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(chunks, "chunks");
        Objects.requireNonNull(work, "work");
        if (!scheduledIds.add(id)) {
            return false;
        }
        pending.add(new Job<>(id, level, List.copyOf(new LinkedHashSet<>(chunks)), work));
        return true;
    }

    public void tick() {
        if (active == null) {
            active = pending.poll();
            if (active == null) {
                return;
            }
        }

        switch (active.phase) {
            case ACQUIRE -> acquireNextChunks();
            case WAIT -> runWhenReady();
            case RELEASE -> releaseNextChunks();
        }
    }

    public boolean isIdle() {
        return active == null && pending.isEmpty();
    }

    public void clear() {
        if (active != null) {
            int firstUnreleased = active.phase == Phase.RELEASE ? active.nextReleaseIndex : 0;
            for (int i = firstUnreleased; i < active.ownedChunks.size(); i++) {
                backend.release(active.level, active.ownedChunks.get(i));
            }
        }
        active = null;
        pending.clear();
        scheduledIds.clear();
    }

    private void acquireNextChunks() {
        int count = 0;
        while (count < requestsPerTick && active.nextChunkIndex < active.chunks.size()) {
            ChunkPos chunk = active.chunks.get(active.nextChunkIndex++);
            if (backend.acquire(active.level, chunk)) {
                active.ownedChunks.add(chunk);
            }
            count++;
        }
        if (active.nextChunkIndex >= active.chunks.size()) {
            active.phase = Phase.WAIT;
        }
    }

    private void runWhenReady() {
        for (ChunkPos chunk : active.chunks) {
            if (!backend.isForced(active.level, chunk)) {
                if (backend.acquire(active.level, chunk)
                        && !active.ownedChunks.contains(chunk)) {
                    active.ownedChunks.add(chunk);
                }
                return;
            }
            if (!backend.isLoaded(active.level, chunk)) {
                return;
            }
        }
        try {
            active.work.run();
        } catch (RuntimeException exception) {
            backend.onFailure(active.id, exception);
        }
        active.phase = Phase.RELEASE;
    }

    private void releaseNextChunks() {
        int count = 0;
        while (count < releasesPerTick && active.nextReleaseIndex < active.ownedChunks.size()) {
            backend.release(active.level, active.ownedChunks.get(active.nextReleaseIndex++));
            count++;
        }
        if (active.nextReleaseIndex >= active.ownedChunks.size()) {
            scheduledIds.remove(active.id);
            active = null;
        }
    }

    private enum Phase {
        ACQUIRE,
        WAIT,
        RELEASE
    }

    private static final class Job<L> {
        final String id;
        final L level;
        final List<ChunkPos> chunks;
        final Runnable work;
        final List<ChunkPos> ownedChunks = new ArrayList<>();
        int nextChunkIndex;
        int nextReleaseIndex;
        Phase phase = Phase.ACQUIRE;

        Job(String id, L level, List<ChunkPos> chunks, Runnable work) {
            this.id = id;
            this.level = level;
            this.chunks = chunks;
            this.work = work;
        }
    }
}
