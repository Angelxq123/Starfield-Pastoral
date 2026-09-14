package com.stardew.craft.building.runtime;

import net.minecraft.core.BlockPos;

/** Integer block volume: minimum inclusive, maximum exclusive. Touching faces do not overlap. */
public record BuildingBounds(BlockPos min, BlockPos maxExclusive) {
    public BuildingBounds {
        min = min.immutable();
        maxExclusive = maxExclusive.immutable();
        if (min.getX() >= maxExclusive.getX() || min.getY() >= maxExclusive.getY()
                || min.getZ() >= maxExclusive.getZ()) {
            throw new IllegalArgumentException("Building bounds must have positive dimensions");
        }
    }

    public boolean contains(BlockPos pos) {
        return pos.getX() >= min.getX() && pos.getX() < maxExclusive.getX()
                && pos.getY() >= min.getY() && pos.getY() < maxExclusive.getY()
                && pos.getZ() >= min.getZ() && pos.getZ() < maxExclusive.getZ();
    }

    public boolean intersects(BuildingBounds other) {
        return min.getX() < other.maxExclusive.getX() && maxExclusive.getX() > other.min.getX()
                && min.getY() < other.maxExclusive.getY() && maxExclusive.getY() > other.min.getY()
                && min.getZ() < other.maxExclusive.getZ() && maxExclusive.getZ() > other.min.getZ();
    }

    public BlockPos maxInclusive() {
        return maxExclusive.offset(-1, -1, -1);
    }
}
