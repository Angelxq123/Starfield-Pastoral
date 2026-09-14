package com.stardew.craft.block.terrain;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;

/** The same 47-neighborhood topology as paving, connected only to playground sand at this elevation. */
public final class PlaygroundSandConnections {
    private static final int[][] OFFSETS = {{0,-1},{1,0},{0,1},{-1,0},{1,-1},{1,1},{-1,1},{-1,-1}};
    private PlaygroundSandConnections() {}

    public static int mask(BlockGetter level, BlockPos pos) {
        int result = 0;
        for (int i = 0; i < OFFSETS.length; i++) {
            if (level.getBlockState(pos.offset(OFFSETS[i][0], 0, OFFSETS[i][1])).getBlock() instanceof PlaygroundSandBlock)
                result |= 1 << i;
        }
        return TownPavingConnections.canonical(result);
    }

    public static int row(int mask) { return TownPavingConnections.row(mask); }
}
