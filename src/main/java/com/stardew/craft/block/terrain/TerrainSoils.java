package com.stardew.craft.block.terrain;

import com.stardew.craft.block.ModBlocks;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Keep the authored substrate when cultivation is removed or only its soil face is sampled. */
public final class TerrainSoils {
    private TerrainSoils() {}

    public static boolean farmland(BlockState state) {
        return state.getBlock() instanceof TerrainFarmlandBlock;
    }

    public static boolean sandy(BlockState state) {
        return state.is(ModBlocks.SAND.get()) || state.is(ModBlocks.SANDY_FARMLAND.get());
    }

    public static boolean bare(BlockState state) {
        return state.is(ModBlocks.DIRT.get()) || state.is(ModBlocks.SAND.get());
    }

    public static Block substrate(BlockState state) {
        return sandy(state) ? ModBlocks.SAND.get() : ModBlocks.DIRT.get();
    }

    /** Preserve the existing yellow-soil return for legacy/vanilla farm soil. */
    public static Block restored(BlockState state) {
        return farmland(state) ? substrate(state) : ModBlocks.YELLOW_DIRT.get();
    }
}
