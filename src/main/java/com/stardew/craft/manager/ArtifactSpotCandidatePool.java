package com.stardew.craft.manager;

import com.stardew.craft.api.v1.world.StardewRegion;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import java.util.List;
import java.util.function.Predicate;

/** Lazy random permutation of this map's columns; failed terrain checks do not consume spawn attempts. */
public final class ArtifactSpotCandidatePool {
    private final long[] columns;
    private final RandomSource random;
    private int remaining;

    public ArtifactSpotCandidatePool(List<StardewRegion.Box> boxes, RandomSource random) {
        var unique = new LongOpenHashSet();
        for (var box : boxes) {
            for (int x = box.min().getX(); x <= box.max().getX(); x++) {
                for (int z = box.min().getZ(); z <= box.max().getZ(); z++) unique.add(BlockPos.asLong(x, 0, z));
            }
        }
        columns = unique.toLongArray();
        remaining = columns.length;
        this.random = random;
    }

    public int inspected() { return columns.length - remaining; }

    public BlockPos drawColumn() {
        if (remaining == 0) return null;
        int index = random.nextInt(remaining);
        long packed = columns[index];
        columns[index] = columns[--remaining];
        return BlockPos.of(packed);
    }

    /** Only probe until the next usable position is found. Each column is visited at most once per pass. */
    public BlockPos next(ServerLevel level, Predicate<BlockPos> allowedGround) {
        BlockPos column;
        while ((column = drawColumn()) != null) {
            level.getChunk(column.getX() >> 4, column.getZ() >> 4);
            var marker = ArtifactSpotSpawnService.surfaceMarker(level, column.getX(), column.getZ());
            if (ArtifactSpotSpawnService.isDiggableSurface(level.getBlockState(marker.below()))
                    && allowedGround.test(marker.below()) && ArtifactSpotSpawnService.canPlace(level, marker)) return marker;
        }
        return null;
    }
}
