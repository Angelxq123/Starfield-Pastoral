package com.stardew.craft.floor;

import java.util.Arrays;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;

/** Native artwork order: N/E/S/W/NE/NW/SE/SW. Connections never cross height or material. */
public final class SurfaceFloorConnections {
    public static final int[][] OFFSETS = {{0,-1},{1,0},{0,1},{-1,0},{1,-1},{-1,-1},{1,1},{-1,1}};
    private static final int[] PAIRS = {3, 9, 6, 12};
    private static final int[] MASKS = java.util.stream.IntStream.range(0, 256)
            .filter(m -> canonical(m) == m).toArray();

    private SurfaceFloorConnections() {}

    public static int canonical(int mask) {
        for (int i = 0; i < 4; i++) if ((mask & PAIRS[i]) != PAIRS[i]) mask &= ~(16 << i);
        return mask & 255;
    }

    public static int mask(BlockPos pos, Predicate<BlockPos> matches) {
        int mask = 0;
        for (int i = 0; i < OFFSETS.length; i++)
            if (matches.test(pos.offset(OFFSETS[i][0], 0, OFFSETS[i][1]))) mask |= 1 << i;
        return canonical(mask);
    }

    public static int row(int mask) { return Arrays.binarySearch(MASKS, canonical(mask)); }

    public static boolean hasHay(BlockPos pos, Predicate<BlockPos> matches) {
        return matches.test(pos) && mask(pos, matches) == 255;
    }

    public static int phase(SurfaceFloorType type, BlockPos pos) {
        return Math.floorMod(pos.getX(), type.phaseX) + type.phaseX * Math.floorMod(pos.getZ(), type.phaseZ);
    }

    public static int townMask(int mask) {
        return (mask & 31) | ((mask & 64) >> 1) | ((mask & 128) >> 1) | ((mask & 32) << 2);
    }
}
