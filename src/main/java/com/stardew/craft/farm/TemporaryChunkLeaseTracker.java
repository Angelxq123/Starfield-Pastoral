package com.stardew.craft.farm;

import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

final class TemporaryChunkLeaseTracker<L> {

    interface Backend<L> {
        boolean acquire(L level, ChunkPos chunk);

        void release(L level, ChunkPos chunk);
    }

    interface Lease extends AutoCloseable {
        @Override
        void close();
    }

    private static final Lease NO_OP_LEASE = () -> {};

    private final Backend<L> backend;
    private final IdentityHashMap<L, Map<ChunkPos, Entry<L>>> entriesByLevel = new IdentityHashMap<>();

    TemporaryChunkLeaseTracker(Backend<L> backend) {
        this.backend = Objects.requireNonNull(backend, "backend");
    }

    synchronized Lease acquire(L level, Collection<ChunkPos> chunks) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(chunks, "chunks");

        LinkedHashSet<ChunkPos> distinctChunks = new LinkedHashSet<>();
        for (ChunkPos chunk : chunks) {
            distinctChunks.add(Objects.requireNonNull(chunk, "chunk"));
        }
        if (distinctChunks.isEmpty()) {
            return NO_OP_LEASE;
        }

        Map<ChunkPos, Entry<L>> levelEntries = entriesByLevel.computeIfAbsent(level, ignored -> new HashMap<>());
        List<Entry<L>> acquiredEntries = new ArrayList<>(distinctChunks.size());
        try {
            for (ChunkPos chunk : distinctChunks) {
                Entry<L> entry = levelEntries.get(chunk);
                if (entry == null) {
                    entry = new Entry<>(level, chunk, backend.acquire(level, chunk));
                    levelEntries.put(chunk, entry);
                } else {
                    entry.references++;
                }
                acquiredEntries.add(entry);
            }
        } catch (RuntimeException exception) {
            rollback(level, levelEntries, acquiredEntries, exception);
            throw exception;
        }

        return new TrackedLease(acquiredEntries);
    }

    synchronized void closeAll(L level) {
        Objects.requireNonNull(level, "level");
        Map<ChunkPos, Entry<L>> levelEntries = entriesByLevel.remove(level);
        if (levelEntries != null) {
            closeEntries(levelEntries.values());
        }
    }

    synchronized void closeAll() {
        List<Entry<L>> entries = entriesByLevel.values().stream()
            .flatMap(levelEntries -> levelEntries.values().stream())
            .toList();
        entriesByLevel.clear();
        closeEntries(entries);
    }

    private void rollback(L level, Map<ChunkPos, Entry<L>> levelEntries,
                          List<Entry<L>> acquiredEntries, RuntimeException failure) {
        for (int index = acquiredEntries.size() - 1; index >= 0; index--) {
            Entry<L> entry = acquiredEntries.get(index);
            entry.references--;
            if (entry.references == 0) {
                levelEntries.remove(entry.chunk, entry);
                entry.active = false;
                releaseOwned(entry, failure);
            }
        }
        if (levelEntries.isEmpty()) {
            entriesByLevel.remove(level);
        }
    }

    private synchronized void closeLease(List<Entry<L>> leaseEntries) {
        RuntimeException failure = null;
        for (Entry<L> entry : leaseEntries) {
            if (!entry.active || --entry.references > 0) {
                continue;
            }

            entry.active = false;
            Map<ChunkPos, Entry<L>> levelEntries = entriesByLevel.get(entry.level);
            if (levelEntries != null) {
                levelEntries.remove(entry.chunk, entry);
                if (levelEntries.isEmpty()) {
                    entriesByLevel.remove(entry.level);
                }
            }
            failure = releaseOwned(entry, failure);
        }
        if (failure != null) {
            throw failure;
        }
    }

    private void closeEntries(Collection<Entry<L>> entries) {
        RuntimeException failure = null;
        for (Entry<L> entry : entries) {
            if (!entry.active) {
                continue;
            }
            entry.active = false;
            entry.references = 0;
            failure = releaseOwned(entry, failure);
        }
        if (failure != null) {
            throw failure;
        }
    }

    private RuntimeException releaseOwned(Entry<L> entry, RuntimeException failure) {
        if (!entry.owned) {
            return failure;
        }
        try {
            backend.release(entry.level, entry.chunk);
        } catch (RuntimeException releaseFailure) {
            if (failure == null) {
                return releaseFailure;
            }
            failure.addSuppressed(releaseFailure);
        }
        return failure;
    }

    private static final class Entry<L> {
        private final L level;
        private final ChunkPos chunk;
        private final boolean owned;
        private int references = 1;
        private boolean active = true;

        private Entry(L level, ChunkPos chunk, boolean owned) {
            this.level = level;
            this.chunk = chunk;
            this.owned = owned;
        }
    }

    private final class TrackedLease implements Lease {
        private final List<Entry<L>> entries;
        private boolean closed;

        private TrackedLease(List<Entry<L>> entries) {
            this.entries = List.copyOf(entries);
        }

        @Override
        public synchronized void close() {
            if (closed) {
                return;
            }
            closed = true;
            closeLease(entries);
        }
    }
}
