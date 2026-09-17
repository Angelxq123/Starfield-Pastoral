package com.stardew.craft.warp;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;

/** Server-thread requests: cold chunks never block, and every exit releases its lease. */
public final class PendingTeleportQueue<R extends PendingTeleportQueue.Request> {
    public interface Request extends AutoCloseable {
        boolean isValid();
        boolean isReady();
        void complete();
        @Override void close();
    }

    private record Entry<R>(R request, int started) {}
    private final Map<UUID, Entry<R>> pending = new HashMap<>();
    private final ArrayDeque<UUID> order = new ArrayDeque<>();
    private final int maxChecks, timeout;
    private final long budget;
    private final LongSupplier clock;

    public PendingTeleportQueue(int maxChecks, long budget, int timeout, LongSupplier clock) {
        if (maxChecks < 1 || budget < 1 || timeout < 1) throw new IllegalArgumentException();
        this.maxChecks = maxChecks;
        this.budget = budget;
        this.timeout = timeout;
        this.clock = java.util.Objects.requireNonNull(clock);
    }

    public boolean contains(UUID id) { return pending.containsKey(id); }

    /** Caller retains responsibility for a rejected duplicate request. */
    public boolean enqueue(UUID id, R request, int tick) {
        if (contains(id)) return false;
        pending.put(id, new Entry<>(request, tick));
        order.addLast(id);
        return true;
    }

    public void tick(int tick) {
        long start = clock.getAsLong();
        int count = Math.min(maxChecks, order.size());
        for (int i = 0; i < count && (i == 0 || clock.getAsLong() - start < budget); i++) {
            UUID id = order.removeFirst();
            Entry<R> entry = pending.get(id);
            boolean retain = false;
            try {
                if (tick - entry.started() >= timeout || !entry.request().isValid()) continue;
                if (!entry.request().isReady()) {
                    retain = true;
                    continue;
                }
                entry.request().complete();
            } finally {
                if (retain) order.addLast(id);
                else {
                    pending.remove(id);
                    entry.request().close();
                }
            }
        }
    }

    public void clear() {
        RuntimeException failure = null;
        for (Entry<R> entry : pending.values()) {
            try { entry.request().close(); }
            catch (RuntimeException e) { if (failure == null) failure = e; else failure.addSuppressed(e); }
        }
        pending.clear();
        order.clear();
        if (failure != null) throw failure;
    }
}
