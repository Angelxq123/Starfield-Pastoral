package com.stardew.craft.farm;

import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.terrain.TerrainSoils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Applied once, immediately after a new farm's terrain is placed. Never migrates existing farms. */
public final class FarmSubsoil {
    private FarmSubsoil() {}

    public static int replaceBuriedDirt(ServerLevel level, BlockPos min, BlockPos max) {
        int changed = 0;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = min.getX(); x <= max.getX(); x++) {
            for (int z = min.getZ(); z <= max.getZ(); z++) {
                boolean belowSurface = false;
                for (int y = max.getY(); y >= min.getY(); y--) {
                    pos.set(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    if (belowSurface && dirt(state)) {
                        level.setBlock(pos, ModBlocks.HARD_SOIL.get().defaultBlockState(), Block.UPDATE_CLIENTS);
                        changed++;
                    }
                    if (surface(state)) belowSurface = true;
                }
            }
        }
        return changed;
    }

    private static boolean dirt(BlockState state) {
        return state.is(ModBlocks.DIRT.get()) || state.is(ModBlocks.YELLOW_DIRT.get())
                || state.is(Blocks.DIRT) || state.is(Blocks.COARSE_DIRT);
    }

    private static boolean surface(BlockState state) {
        return dirt(state) || TerrainSoils.bare(state) || TerrainSoils.farmland(state)
                || state.is(ModBlocks.GRASS_BLOCK.get()) || state.is(ModBlocks.DARK_GRASS_BLOCK.get())
                || state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.SAND)
                || state.is(ModBlocks.CLIFF.get()) || state.is(net.minecraft.tags.BlockTags.BASE_STONE_OVERWORLD);
    }
}
