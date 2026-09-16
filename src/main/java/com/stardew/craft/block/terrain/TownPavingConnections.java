package com.stardew.craft.block.terrain;

import java.util.Arrays;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;

/** N/E/S/W/NE/SE/SW/NW. A diagonal affects a miter only when both rails connect. */
public final class TownPavingConnections {
    private static final int[][] OFFSETS = {{0,-1},{1,0},{0,1},{-1,0},{1,-1},{1,1},{-1,1},{-1,-1}};
    private static final int[] MASKS = java.util.stream.IntStream.range(0, 256)
            .filter(mask -> canonical(mask) == mask).toArray();

    private TownPavingConnections() {}

    public static int canonical(int mask) {
        for (int i = 0; i < 4; i++) {
            int adjacent = (1 << i) | (1 << ((i + 1) % 4));
            if ((mask & adjacent) != adjacent) mask &= ~(1 << (i + 4));
        }
        return mask & 255;
    }

    public static int mask(BlockGetter level, BlockPos pos) {
        int result = 0;
        for (int i = 0; i < OFFSETS.length; i++) {
            if (level.getBlockState(pos.offset(OFFSETS[i][0], 0, OFFSETS[i][1])).getBlock() instanceof TownPavingBlock)
                result |= 1 << i;
        }
        return canonical(result);
    }

    /** Complement before canonicalization: an isolated red diagonal still paints a corner. */
    public static int redTransitionRow(BlockGetter level, BlockPos pos) {
        int red = 0;
        for (int i = 0; i < OFFSETS.length; i++) {
            if (level.getBlockState(pos.offset(OFFSETS[i][0], 0, OFFSETS[i][1]))
                    .is(com.stardew.craft.block.ModBlocks.PLAZA_RED_BRICKS.get())) red |= 1 << i;
        }
        return row(255 ^ red);
    }

    public static int row(int mask) {
        return Arrays.binarySearch(MASKS, canonical(mask));
    }

    public static int phase(BlockPos pos) {
        return Math.floorMod(pos.getX(), 2) + 2 * Math.floorMod(pos.getZ(), 2);
    }
}
