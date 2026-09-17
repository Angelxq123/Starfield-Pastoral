package com.stardew.craft.block.terrain;

import com.stardew.craft.block.ModBlocks;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Keep the authored substrate when cultivation is removed or only its soil face is sampled. */
public final class TerrainSoils {
    private TerrainSoils() {}

    public static boolean farmland(BlockState state) {
        return state.is(ModBlocks.FARMLAND.get()) || state.is(ModBlocks.SANDY_FARMLAND.get())
                || state.is(ModBlocks.INFERTILE_FARMLAND.get());
    }

    /** Planting is an explicit identity allowlist, never a tag, subclass or name match. */
    public static boolean cropSupport(BlockState state) {
        return farmland(state) || state.is(ModBlocks.GARDEN_POT.get());
    }

    public static boolean treeSeedGround(BlockState state) {
        return state.is(ModBlocks.DIRT.get()) || state.is(ModBlocks.HARD_SOIL.get())
                || state.is(ModBlocks.YELLOW_DIRT.get()) || state.is(ModBlocks.GRASS_BLOCK.get())
                || state.is(ModBlocks.DARK_GRASS_BLOCK.get());
    }

    public static boolean treeGround(BlockState state) {
        return treeSeedGround(state) || farmland(state);
    }

    public static boolean sandy(BlockState state) {
        return state.is(ModBlocks.SAND.get()) || state.is(ModBlocks.SANDY_FARMLAND.get());
    }

    public static boolean infertile(BlockState state) {
        return state.is(ModBlocks.HARD_SOIL.get()) || state.is(ModBlocks.INFERTILE_FARMLAND.get());
    }

    public static boolean blocksSprinklers(BlockState state) {
        return sandy(state) || infertile(state);
    }

    /** 0 ordinary soil, 1 sand, 2 compact subsoil; all keep their own surface. */
    public static int family(BlockState state) {
        return infertile(state) ? 2 : sandy(state) ? 1 : 0;
    }

    public static boolean bare(BlockState state) {
        return state.is(ModBlocks.DIRT.get()) || state.is(ModBlocks.SAND.get()) || state.is(ModBlocks.HARD_SOIL.get());
    }

    public static Block substrate(BlockState state) {
        return infertile(state) ? ModBlocks.HARD_SOIL.get() : sandy(state) ? ModBlocks.SAND.get() : ModBlocks.DIRT.get();
    }

    /** Imported farmland must never become our plantable soil through overnight decay. */
    public static Block restored(BlockState state) {
        return farmland(state) ? substrate(state) : net.minecraft.world.level.block.Blocks.DIRT;
    }
}
