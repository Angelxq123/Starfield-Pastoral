package com.stardew.craft.event;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FarmEntryTeleportQueueTest {

    private static final BlockPos TARGET = new BlockPos(160, 70, -80);

    @Test
    void waitsForCenterChunkWithoutSynchronouslyLoadingOuterChunks() {
        RecordingBackend backend = new RecordingBackend();
        FarmEntryTeleportQueue<TestLevel, TestPlayer> queue =
                new FarmEntryTeleportQueue<>(backend, 8);
        TestLevel level = new TestLevel("valley");
        TestPlayer player = new TestPlayer();

        queue.enqueue(player.id, level, player, TARGET);
        queue.tick();

        assertEquals(List.of(new ChunkPos(TARGET)), backend.acquired);
        assertEquals(List.of(), backend.teleported);

        backend.loaded.add(new ChunkPos(TARGET));
        queue.tick();

        assertEquals(List.of(player.id), backend.teleported);
        assertEquals(List.of(new ChunkPos(TARGET)), backend.released);
        assertEquals(0, queue.pendingCount());
    }

    @Test
    void replacingRequestAndInvalidPlayersReleaseOwnedTickets() {
        RecordingBackend backend = new RecordingBackend();
        FarmEntryTeleportQueue<TestLevel, TestPlayer> queue =
                new FarmEntryTeleportQueue<>(backend, 8);
        TestLevel level = new TestLevel("valley");
        TestPlayer player = new TestPlayer();
        BlockPos replacement = TARGET.offset(32, 0, 0);

        queue.enqueue(player.id, level, player, TARGET);
        queue.enqueue(player.id, level, player, replacement);
        player.valid = false;
        queue.tick();

        assertEquals(
                List.of(new ChunkPos(TARGET), new ChunkPos(replacement)),
                backend.acquired);
        assertEquals(
                List.of(new ChunkPos(TARGET), new ChunkPos(replacement)),
                backend.released);
        assertEquals(List.of(), backend.teleported);
    }

    @Test
    void checksPendingPlayersInBoundedRoundRobinBatches() {
        RecordingBackend backend = new RecordingBackend();
        FarmEntryTeleportQueue<TestLevel, TestPlayer> queue =
                new FarmEntryTeleportQueue<>(backend, 2);
        TestLevel level = new TestLevel("valley");
        TestPlayer first = new TestPlayer();
        TestPlayer second = new TestPlayer();
        TestPlayer third = new TestPlayer();

        queue.enqueue(first.id, level, first, TARGET);
        queue.enqueue(second.id, level, second, TARGET.offset(16, 0, 0));
        queue.enqueue(third.id, level, third, TARGET.offset(32, 0, 0));

        queue.tick();
        queue.tick();

        assertEquals(List.of(first.id, second.id, third.id, first.id), backend.validityChecks);
    }

    private static final class TestLevel {
        private final String name;

        private TestLevel(String name) {
            this.name = name;
        }
    }

    private static final class TestPlayer {
        private final UUID id = UUID.randomUUID();
        private boolean valid = true;
    }

    private static final class RecordingBackend
            implements FarmEntryTeleportQueue.Backend<TestLevel, TestPlayer> {
        private final List<ChunkPos> acquired = new ArrayList<>();
        private final List<ChunkPos> released = new ArrayList<>();
        private final List<UUID> teleported = new ArrayList<>();
        private final List<UUID> validityChecks = new ArrayList<>();
        private final Set<ChunkPos> loaded = new HashSet<>();

        @Override
        public boolean acquire(TestLevel level, ChunkPos chunk) {
            acquired.add(chunk);
            return true;
        }

        @Override
        public boolean isLoaded(TestLevel level, ChunkPos chunk) {
            return loaded.contains(chunk);
        }

        @Override
        public boolean isValid(UUID playerId, TestLevel level, TestPlayer player) {
            validityChecks.add(playerId);
            return player.valid;
        }

        @Override
        public void teleport(TestLevel level, TestPlayer player, BlockPos target) {
            teleported.add(player.id);
        }

        @Override
        public void release(TestLevel level, ChunkPos chunk) {
            released.add(chunk);
        }
    }
}
