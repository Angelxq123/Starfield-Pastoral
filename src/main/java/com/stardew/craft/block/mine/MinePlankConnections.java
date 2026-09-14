package com.stardew.craft.block.mine;

import com.stardew.craft.block.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Wood extends over exposed, same-height soil. Bits: N/E/S/W then NE/SE/SW/NW. */
public final class MinePlankConnections {
    private static final int[][] OFFSETS = {{0,-1},{1,0},{0,1},{-1,0},{1,-1},{1,1},{-1,1},{-1,-1}};

    private MinePlankConnections() {}

    public static int mask(BlockGetter level, BlockPos pos, BlockState state) {
        int combined = 0;
        for (int mask : themeMasks(level, pos, state)) combined |= mask;
        return combined;
    }

    public static int[] themeMasks(BlockGetter level, BlockPos pos, BlockState state) {
        int[] masks = new int[MineBuildingTheme.values().length];
        if (!(state.getBlock() instanceof MineSoilBlock || state.is(ModBlocks.DIRT.get())) || !open(level, pos)) return masks;
        int mask = 0;
        for (int i = 0; i < OFFSETS.length; i++) {
            BlockPos neighbor = pos.offset(OFFSETS[i][0], 0, OFFSETS[i][1]);
            BlockState donor = level.getBlockState(neighbor);
            if (donor.is(ModBlocks.MINE_PLANKS.get()) && open(level, neighbor)) {
                mask |= 1 << i;
                masks[donor.getValue(MineBuildingTheme.PROPERTY).ordinal()] |= 1 << i;
            }
        }
        int canonical = canonical(mask);
        for (int i = 0; i < masks.length; i++) masks[i] &= canonical;
        return masks;
    }

    private static boolean open(BlockGetter level, BlockPos pos) {
        BlockPos abovePos = pos.above();
        BlockState above = level.getBlockState(abovePos);
        return above.getFluidState().isEmpty() && !above.is(Blocks.SNOW)
                && above.getCollisionShape(level, abovePos).isEmpty();
    }

    public static int canonical(int mask) {
        int result = mask & 255;
        for (int corner = 0; corner < 4; corner++) {
            // A cardinal edge owns both of its corners: no doubled diagonal overlay.
            if ((mask & ((1 << corner) | (1 << ((corner + 1) % 4)))) != 0) result &= ~(16 << corner);
        }
        return result;
    }
}
