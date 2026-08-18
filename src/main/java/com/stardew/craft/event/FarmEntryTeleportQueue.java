package com.stardew.craft.event;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

final class FarmEntryTeleportQueue<L, P> {

    interface Backend<L, P> {
        boolean acquire(L level, ChunkPos chunk);

        boolean isLoaded(L level, ChunkPos chunk);

        boolean isValid(UUID playerId, L level, P player);

        void teleport(L level, P player, BlockPos target);

        void release(L level, ChunkPos chunk);
    }

    private final Backend<L, P> backend;
    private final int maxChecksPerTick;
    private final Map<UUID, Pending<L, P>> pendingByPlayer = new HashMap<>();
    private final ArrayDeque<UUID> order = new ArrayDeque<>();

    FarmEntryTeleportQueue(Backend<L, P> backend, int maxChecksPerTick) {
        this.backend = Objects.requireNonNull(backend, "backend");
        if (maxChecksPerTick <= 0) {
            throw new IllegalArgumentException("maxChecksPerTick must be positive");
        }
        this.maxChecksPerTick = maxChecksPerTick;
    }

    synchronized void enqueue(UUID playerId, L level, P player, BlockPos target) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(target, "target");

        Pending<L, P> previous = pendingByPlayer.remove(playerId);
        order.remove(playerId);
        release(previous);

        BlockPos immutableTarget = target.immutable();
        ChunkPos centerChunk = new ChunkPos(immutableTarget);
        boolean owned = backend.acquire(level, centerChunk);
        pendingByPlayer.put(
                playerId,
                new Pending<>(level, player, immutableTarget, centerChunk, owned));
        order.addLast(playerId);
    }

    synchronized void tick() {
        int checks = Math.min(maxChecksPerTick, order.size());
        for (int index = 0; index < checks; index++) {
            UUID playerId = order.removeFirst();
            Pending<L, P> pending = pendingByPlayer.get(playerId);
            if (pending == null) {
                continue;
            }
            if (!backend.isValid(playerId, pending.level, pending.player)) {
                pendingByPlayer.remove(playerId);
                release(pending);
                continue;
            }
            if (!backend.isLoaded(pending.level, pending.centerChunk)) {
                order.addLast(playerId);
                continue;
            }

            pendingByPlayer.remove(playerId);
            try {
                backend.teleport(pending.level, pending.player, pending.target);
            } finally {
                release(pending);
            }
        }
    }

    synchronized void clear() {
        Throwable failure = null;
        for (Pending<L, P> pending : pendingByPlayer.values()) {
            try {
                release(pending);
            } catch (RuntimeException | Error releaseFailure) {
                if (failure == null) {
                    failure = releaseFailure;
                } else if (failure != releaseFailure) {
                    failure.addSuppressed(releaseFailure);
                }
            }
        }
        pendingByPlayer.clear();
        order.clear();
        if (failure instanceof RuntimeException runtimeException) {
            throw runtimeException;
        }
        if (failure instanceof Error error) {
            throw error;
        }
    }

    synchronized int pendingCount() {
        return pendingByPlayer.size();
    }

    private void release(Pending<L, P> pending) {
        if (pending != null && pending.owned) {
            backend.release(pending.level, pending.centerChunk);
        }
    }

    private static final class Pending<L, P> {
        private final L level;
        private final P player;
        private final BlockPos target;
        private final ChunkPos centerChunk;
        private final boolean owned;

        private Pending(L level, P player, BlockPos target, ChunkPos centerChunk, boolean owned) {
            this.level = level;
            this.player = player;
            this.target = target;
            this.centerChunk = centerChunk;
            this.owned = owned;
        }
    }
}
