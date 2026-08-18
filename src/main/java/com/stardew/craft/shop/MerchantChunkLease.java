package com.stardew.craft.shop;

import com.stardew.craft.event.FixedChunkLease;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

/** Shared non-blocking chunk lease for fixed-position scheduled merchants. */
final class MerchantChunkLease {
    private static final FixedChunkLease.Backend<ServerLevel> BACKEND =
            new FixedChunkLease.Backend<>() {
                @Override
                public boolean isForced(ServerLevel level, ChunkPos chunk) {
                    return level.getForcedChunks().contains(chunk.toLong());
                }

                @Override
                public boolean acquire(ServerLevel level, ChunkPos chunk) {
                    return level.setChunkForced(chunk.x, chunk.z, true);
                }

                @Override
                public boolean isLoaded(ServerLevel level, ChunkPos chunk) {
                    return level.getChunkSource().getChunkNow(chunk.x, chunk.z) != null;
                }

                @Override
                public void release(ServerLevel level, ChunkPos chunk) {
                    level.setChunkForced(chunk.x, chunk.z, false);
                }
            };

    private final ChunkPos chunk;
    private final FixedChunkLease<ServerLevel> lease = new FixedChunkLease<>(BACKEND);

    MerchantChunkLease(BlockPos position) {
        this.chunk = new ChunkPos(position);
    }

    boolean request(ServerLevel level) {
        return lease.request(level, chunk);
    }

    void release() {
        lease.release();
    }

    void clearLegacyTicket(ServerLevel level) {
        lease.release();
        if (level != null && level.getForcedChunks().contains(chunk.toLong())) {
            level.setChunkForced(chunk.x, chunk.z, false);
        }
    }
}
