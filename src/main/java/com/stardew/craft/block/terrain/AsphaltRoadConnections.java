package com.stardew.craft.block.terrain;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;

/** The same 47-neighborhood topology as paving, connected only to asphalt at this elevation. */
public final class AsphaltRoadConnections {
    private static final int[][] OFFSETS = {{0,-1},{1,0},{0,1},{-1,0},{1,-1},{1,1},{-1,1},{-1,-1}};
    private AsphaltRoadConnections() {}

    public static int mask(BlockGetter level, BlockPos pos) {
        int result = 0;
        for (int i = 0; i < OFFSETS.length; i++) {
            if (level.getBlockState(pos.offset(OFFSETS[i][0], 0, OFFSETS[i][1])).getBlock() instanceof AsphaltRoadBlock)
                result |= 1 << i;
        }
        return TownPavingConnections.canonical(result);
    }

    public static int row(int mask) { return TownPavingConnections.row(mask); }
}
