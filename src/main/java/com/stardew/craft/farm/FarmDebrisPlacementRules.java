package com.stardew.craft.farm;

import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.utility.FlooringBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DirtPathBlock;
import net.minecraft.world.level.block.GrassBlock;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

/** Shared placement contract for initial, daily, grass and wild-tree farm ecology. */
public final class FarmDebrisPlacementRules {
    private FarmDebrisPlacementRules() {
    }

    public record Surface(BlockPos ground, BlockPos place, boolean grass) {
    }

    @Nullable
    public static Surface findBareSurface(ServerLevel level, FarmInstance farm, int x, int z) {
        BlockPos min = farm.getFarmBoundsMin();
        BlockPos max = farm.getFarmBoundsMax();
        if (x < min.getX() || x > max.getX() || z < min.getZ() || z > max.getZ()) {
            return null;
        }

        for (int y = max.getY(); y >= min.getY(); y--) {
            BlockPos ground = new BlockPos(x, y, z);
            if (!level.isLoaded(ground)) {
                return null;
            }
            BlockState groundState = level.getBlockState(ground);
            if (groundState.isAir()) {
                continue;
            }

            BlockPos place = ground.above();
            if (!isBareDebrisGround(groundState)
                    || isPlayerFloor(groundState)
                    || !farm.contains(place)
                    || !isCompletelyOpen(level, place)) {
                return null;
            }
            return new Surface(ground.immutable(), place.immutable(), isGrass(groundState));
        }
        return null;
    }

    public static boolean canPlaceYoungTree(ServerLevel level, FarmInstance farm, BlockPos place) {
        if (!farm.contains(place) || !isCompletelyOpen(level, place)) {
            return false;
        }
        BlockState ground = level.getBlockState(place.below());
        return isNaturalFarmGround(ground) && !isPlayerFloor(ground);
    }

    public static boolean canSpreadDebrisAt(ServerLevel level, FarmInstance farm, BlockPos place) {
        if (!farm.contains(place) || isPlayerFloor(level.getBlockState(place))) {
            return false;
        }
        BlockState ground = level.getBlockState(place.below());
        return isSpreadableGround(ground) && !isPlayerFloor(ground);
    }

    public static boolean isCompletelyOpen(ServerLevel level, BlockPos place) {
        BlockState state = level.getBlockState(place);
        return state.isAir() && state.getFluidState().isEmpty() && level.getBlockEntity(place) == null;
    }

    public static boolean isBareDebrisGround(BlockState state) {
        return isNaturalFarmGround(state)
                && !com.stardew.craft.block.terrain.TerrainSoils.farmland(state);
    }

    public static boolean isSpreadableGround(BlockState state) {
        return isNaturalFarmGround(state)
                || com.stardew.craft.block.terrain.TerrainSoils.farmland(state)
                || state.is(Blocks.FARMLAND);
    }

    public static boolean isNaturalFarmGround(BlockState state) {
        Block block = state.getBlock();
        return block == ModBlocks.DIRT.get()
                || block == ModBlocks.HARD_SOIL.get()
                || block == ModBlocks.YELLOW_DIRT.get()
                || block == ModBlocks.GRASS_BLOCK.get()
                || block == ModBlocks.DARK_GRASS_BLOCK.get()
                || block == Blocks.DIRT
                || block instanceof GrassBlock;
    }

    public static boolean isNaturalFarmGround(Block block) {
        return block == ModBlocks.DIRT.get()
                || block == ModBlocks.HARD_SOIL.get()
                || block == ModBlocks.YELLOW_DIRT.get()
                || block == ModBlocks.GRASS_BLOCK.get()
                || block == ModBlocks.DARK_GRASS_BLOCK.get()
                || block == Blocks.DIRT
                || block instanceof GrassBlock;
    }

    public static boolean isGrass(BlockState state) {
        return state.getBlock() instanceof GrassBlock;
    }

    public static boolean isPlayerFloor(BlockState state) {
        return state.getBlock() instanceof FlooringBlock
                || state.getBlock() instanceof DirtPathBlock;
    }
}
