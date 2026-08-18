package com.stardew.craft.npc.runtime;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FarmAreaClearCursorTest {

    @Test
    void visitsFarmBlocksChunkByChunkWithoutLeavingBounds() {
        FarmAreaClearCursor cursor = new FarmAreaClearCursor(
                new BlockPos(15, 4, 2), new BlockPos(17, 5, 3));
        List<ChunkPos> chunks = new ArrayList<>();
        List<BlockPos> blocks = new ArrayList<>();

        while (!cursor.isComplete()) {
            chunks.add(cursor.currentChunk());
            while (cursor.hasBlockInCurrentChunk()) {
                blocks.add(cursor.currentBlock());
                cursor.advanceBlock();
            }
            cursor.advanceChunk();
        }

        assertEquals(List.of(new ChunkPos(0, 0), new ChunkPos(1, 0)), chunks);
        assertEquals(12, blocks.size());
        assertTrue(blocks.contains(new BlockPos(15, 4, 2)));
        assertTrue(blocks.contains(new BlockPos(17, 5, 3)));
        assertFalse(blocks.stream().anyMatch(pos -> pos.getX() < 15 || pos.getX() > 17));
    }

    @Test
    void refusesToAdvanceChunkBeforeItsBlockCursorIsDrained() {
        FarmAreaClearCursor cursor = new FarmAreaClearCursor(
                BlockPos.ZERO, new BlockPos(0, 0, 0));

        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalStateException.class, cursor::advanceChunk);
        cursor.advanceBlock();
        cursor.advanceChunk();

        assertTrue(cursor.isComplete());
    }
}
