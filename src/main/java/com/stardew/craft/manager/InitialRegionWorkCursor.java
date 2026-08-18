package com.stardew.craft.manager;

import net.minecraft.world.level.ChunkPos;

final class InitialRegionWorkCursor {
    record Column(int x, int z) {}

    private final int minX;
    private final int maxX;
    private final int minZ;
    private final int maxZ;
    private final int minChunkX;
    private final int maxChunkX;
    private final int maxChunkZ;

    private int requestChunkX;
    private int requestChunkZ;
    private boolean chunkRequestsComplete;
    private int x;
    private int z;
    private boolean columnsComplete;

    InitialRegionWorkCursor(int minX, int maxX, int minZ, int maxZ) {
        if (minX > maxX || minZ > maxZ) {
            throw new IllegalArgumentException("region bounds must not be inverted");
        }
        this.minX = minX;
        this.maxX = maxX;
        this.minZ = minZ;
        this.maxZ = maxZ;
        minChunkX = minX >> 4;
        maxChunkX = maxX >> 4;
        requestChunkX = minChunkX;
        requestChunkZ = minZ >> 4;
        maxChunkZ = maxZ >> 4;
        x = minX;
        z = minZ;
    }

    boolean hasChunkRequest() {
        return !chunkRequestsComplete;
    }

    ChunkPos pollChunkRequest() {
        if (chunkRequestsComplete) {
            throw new IllegalStateException("all chunk requests have been issued");
        }
        ChunkPos result = new ChunkPos(requestChunkX, requestChunkZ);
        if (requestChunkX < maxChunkX) {
            requestChunkX++;
        } else if (requestChunkZ < maxChunkZ) {
            requestChunkX = minChunkX;
            requestChunkZ++;
        } else {
            chunkRequestsComplete = true;
        }
        return result;
    }

    boolean hasColumn() {
        return chunkRequestsComplete && !columnsComplete;
    }

    Column currentColumn() {
        if (!hasColumn()) {
            throw new IllegalStateException("no region column is available");
        }
        return new Column(x, z);
    }

    void advanceColumn() {
        currentColumn();
        if (z < maxZ) {
            z++;
        } else if (x < maxX) {
            x++;
            z = minZ;
        } else {
            columnsComplete = true;
        }
    }
}
