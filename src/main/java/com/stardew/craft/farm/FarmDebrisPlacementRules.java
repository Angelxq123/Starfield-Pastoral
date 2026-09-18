package com.stardew.craft.farm;

import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.utility.FlooringBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DirtPathBlock;
import net.minecraft.world.level.block.GrassBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;

/** Shared placement contract for initial, daily, grass and wild-tree farm ecology. */
public final class FarmDebrisPlacementRules {
    private FarmDebrisPlacementRules() {
    }

    public enum GroundKind {
        DIRT,
        GRASS,
        DARK_GRASS,
        SAND,
        OTHER
    }

    public record Surface(BlockPos ground, BlockPos place, GroundKind groundKind) {
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
            return new Surface(ground.immutable(), place.immutable(), groundKind(groundState));
        }
        return null;
    }

    /**
     * Minecraft equivalent of an SDV map tile carrying the {@code Diggable}
     * property. Authored landscape grass remains natural terrain, but is not part
     * of the farm field used by daily debris and pasture generation.
     */
    @Nullable
    public static Surface findBareFarmableSurface(
            ServerLevel level, FarmInstance farm, int x, int z
    ) {
        Surface surface = findBareSurface(level, farm, x, z);
        return surface != null && isBareFarmableGround(
                farm, level.getBlockState(surface.ground()))
                ? surface : null;
    }

    /**
     * Walkable Wilderness-monster surface. This intentionally differs from an
     * empty debris tile: collision-free vegetation may remain in the spawn
     * cell, while water, roofs, trees, scenery and portal blocks are rejected.
     */
    @Nullable
    public static BlockPos findMonsterSurface(ServerLevel level, FarmInstance farm, int x, int z) {
        BlockPos min = farm.getFarmBoundsMin();
        BlockPos max = farm.getFarmBoundsMax();
        if (x < min.getX() || x > max.getX() || z < min.getZ() || z > max.getZ()) {
            return null;
        }
        BlockPos probe = new BlockPos(x, farm.getOrigin().getY(), z);
        if (!level.hasChunkAt(probe)) return null;

        for (int y = max.getY(); y >= min.getY(); y--) {
            BlockPos ground = new BlockPos(x, y, z);
            BlockState state = level.getBlockState(ground);
            if (state.isAir() || state.getFluidState().isEmpty()
                    && state.getCollisionShape(level, ground).isEmpty()
                    && level.getBlockEntity(ground) == null) {
                continue;
            }
            if (!state.getFluidState().isEmpty()
                    || !(isNaturalFarmGround(state) || isPlayerFloor(state))
                    || !state.isFaceSturdy(level, ground, Direction.UP)) {
                return null;
            }

            BlockPos place = ground.above();
            if (!farm.contains(place) || !isOpenMonsterCell(level, place)
                    || !isOpenMonsterCell(level, place.above())) {
                return null;
            }
            AABB body = new AABB(
                    place.getX() + 0.05D, place.getY(), place.getZ() + 0.05D,
                    place.getX() + 0.95D, place.getY() + 1.9D, place.getZ() + 0.95D);
            return level.noCollision(body) && !level.containsAnyLiquid(body)
                    ? place.immutable() : null;
        }
        return null;
    }

    private static boolean isOpenMonsterCell(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.getFluidState().isEmpty()
                && state.getCollisionShape(level, pos).isEmpty()
                && level.getBlockEntity(pos) == null
                && !state.is(ModBlocks.PORTAL_TRIGGER.get());
    }

    public static boolean canPlaceYoungTree(ServerLevel level, FarmInstance farm, BlockPos place) {
        if (!farm.contains(place) || !isCompletelyOpen(level, place)) {
            return false;
        }
        return isAutomaticTreeGround(farm, level.getBlockState(place.below()));
    }

    /** Base rule used outside a known farm profile: only authored farm dirt. */
    public static boolean isAutomaticTreeGround(BlockState state) {
        Block block = state.getBlock();
        return (block == ModBlocks.DIRT.get() || block == Blocks.DIRT)
                && !isPlayerFloor(state);
    }

    public static boolean isAutomaticTreeGround(FarmInstance farm, BlockState state) {
        // Farm-aware generation is already constrained to source-derived ecology
        // neighborhoods, so central grass and dark grass are valid without opening
        // decorative hard soil/yellow dirt or non-beach sand to tree spread.
        GroundKind kind = groundKind(state);
        return isAutomaticTreeGround(state)
                || kind == GroundKind.GRASS
                || kind == GroundKind.DARK_GRASS
                || isBeachSand(farm, state);
    }

    public static boolean canSpreadDebrisAt(ServerLevel level, FarmInstance farm, BlockPos place) {
        if (!farm.contains(place) || isPlayerFloor(level.getBlockState(place))) {
            return false;
        }
        BlockState ground = level.getBlockState(place.below());
        return isSpreadableFarmableGround(farm, ground) && !isPlayerFloor(ground);
    }

    public static boolean isCompletelyOpen(ServerLevel level, BlockPos place) {
        BlockState state = level.getBlockState(place);
        return state.isAir() && state.getFluidState().isEmpty() && level.getBlockEntity(place) == null;
    }

    public static boolean isBareDebrisGround(BlockState state) {
        return isNaturalFarmGround(state)
                && !com.stardew.craft.block.terrain.TerrainSoils.farmland(state);
    }

    public static boolean isBareFarmableGround(BlockState state) {
        return groundKind(state) == GroundKind.DIRT && !isPlayerFloor(state);
    }

    public static boolean isBareFarmableGround(FarmInstance farm, BlockState state) {
        return isBareFarmableGround(state)
                || isBeachSand(farm, state);
    }

    public static boolean isSpreadableGround(BlockState state) {
        return isNaturalFarmGround(state)
                || com.stardew.craft.block.terrain.TerrainSoils.farmland(state)
                || state.is(Blocks.FARMLAND);
    }

    public static boolean isSpreadableFarmableGround(BlockState state) {
        return isBareFarmableGround(state)
                || com.stardew.craft.block.terrain.TerrainSoils.farmland(state)
                || state.is(Blocks.FARMLAND);
    }

    public static boolean isSpreadableFarmableGround(FarmInstance farm, BlockState state) {
        return isBareFarmableGround(farm, state)
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
                || block == ModBlocks.SAND.get()
                || block == Blocks.DIRT
                || block == Blocks.SAND
                || block instanceof GrassBlock;
    }

    public static boolean isNaturalFarmGround(Block block) {
        return block == ModBlocks.DIRT.get()
                || block == ModBlocks.HARD_SOIL.get()
                || block == ModBlocks.YELLOW_DIRT.get()
                || block == ModBlocks.GRASS_BLOCK.get()
                || block == ModBlocks.DARK_GRASS_BLOCK.get()
                || block == ModBlocks.SAND.get()
                || block == Blocks.DIRT
                || block == Blocks.SAND
                || block instanceof GrassBlock;
    }

    public static boolean isGrass(BlockState state) {
        return state.getBlock() instanceof GrassBlock;
    }

    /** Stable authored-surface categories used by each farm's initial ecology table. */
    public static GroundKind groundKind(BlockState state) {
        Block block = state.getBlock();
        if (block == ModBlocks.DIRT.get() || block == Blocks.DIRT) {
            return GroundKind.DIRT;
        }
        if (block == ModBlocks.DARK_GRASS_BLOCK.get()) {
            return GroundKind.DARK_GRASS;
        }
        if (block == ModBlocks.GRASS_BLOCK.get() || block instanceof GrassBlock) {
            return GroundKind.GRASS;
        }
        if (block == ModBlocks.SAND.get() || block == Blocks.SAND) {
            return GroundKind.SAND;
        }
        return GroundKind.OTHER;
    }

    private static boolean isBeachSand(FarmInstance farm, BlockState state) {
        return farm.getFarmLayoutId().equals(
                com.stardew.craft.api.v1.internal.farm.StardewFarmLayoutRegistry
                        .builtinId(FarmType.BEACH))
                && groundKind(state) == GroundKind.SAND
                && !isPlayerFloor(state);
    }

    public static boolean isPlayerFloor(BlockState state) {
        return state.getBlock() instanceof FlooringBlock
                || state.getBlock() instanceof DirtPathBlock;
    }
}
