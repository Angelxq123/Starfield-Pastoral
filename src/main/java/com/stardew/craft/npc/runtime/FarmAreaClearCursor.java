package com.stardew.craft.npc.runtime;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;

final class FarmAreaClearCursor {
    private final BlockPos min;
    private final BlockPos max;
    private final int minChunkX;
    private final int maxChunkX;
    private final int maxChunkZ;

    private int chunkX;
    private int chunkZ;
    private int x;
    private int y;
    private int z;
    private int chunkMinX;
    private int chunkMaxX;
    private int chunkMinZ;
    private int chunkMaxZ;
    private boolean hasBlock;
    private boolean complete;

    FarmAreaClearCursor(BlockPos min, BlockPos max) {
        this.min = min.immutable();
        this.max = max.immutable();
        if (min.getX() > max.getX()
                || min.getY() > max.getY()
                || min.getZ() > max.getZ()) {
            throw new IllegalArgumentException("farm bounds must not be inverted");
        }
        minChunkX = min.getX() >> 4;
        maxChunkX = max.getX() >> 4;
        chunkX = minChunkX;
        chunkZ = min.getZ() >> 4;
        maxChunkZ = max.getZ() >> 4;
        resetBlockCursor();
    }

    boolean isComplete() {
        return complete;
    }

    ChunkPos currentChunk() {
        requireActive();
        return new ChunkPos(chunkX, chunkZ);
    }

    boolean hasBlockInCurrentChunk() {
        return !complete && hasBlock;
    }

    BlockPos currentBlock() {
        requireActive();
        if (!hasBlock) {
            throw new IllegalStateException("current chunk has been drained");
        }
        return new BlockPos(x, y, z);
    }

    void advanceBlock() {
        currentBlock();
        if (x < chunkMaxX) {
            x++;
        } else if (z < chunkMaxZ) {
            x = chunkMinX;
            z++;
        } else if (y < max.getY()) {
            x = chunkMinX;
            z = chunkMinZ;
            y++;
        } else {
            hasBlock = false;
        }
    }

    void advanceChunk() {
        requireActive();
        if (hasBlock) {
            throw new IllegalStateException("current chunk still has blocks");
        }
        if (chunkX < maxChunkX) {
            chunkX++;
        } else if (chunkZ < maxChunkZ) {
            chunkX = minChunkX;
            chunkZ++;
        } else {
            complete = true;
            return;
        }
        resetBlockCursor();
    }

    private void resetBlockCursor() {
        chunkMinX = Math.max(min.getX(), chunkX << 4);
        chunkMaxX = Math.min(max.getX(), (chunkX << 4) + 15);
        chunkMinZ = Math.max(min.getZ(), chunkZ << 4);
        chunkMaxZ = Math.min(max.getZ(), (chunkZ << 4) + 15);
        x = chunkMinX;
        y = min.getY();
        z = chunkMinZ;
        hasBlock = true;
    }

    private void requireActive() {
        if (complete) {
            throw new IllegalStateException("farm clear cursor is complete");
        }
    }
}
