package com.stardew.craft.block.mine;

import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.terrain.TerrainFaceConnections;
import com.stardew.craft.block.terrain.TownPavingConnections;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;

/** The 47 connected neighborhoods, evaluated in each exposed cube face's UV frame. */
public final class DesertWallConnections {
    private DesertWallConnections() {}

    public static int mask(BlockGetter level, BlockPos pos, Direction face) {
        int mask = 0;
        for (int edge = 0; edge < 4; edge++) {
            Direction tangent = TerrainFaceConnections.tangent(face, edge);
            if (connects(level, pos.relative(tangent), face)) mask |= 1 << edge;
            BlockPos diagonal = pos.relative(tangent).relative(TerrainFaceConnections.tangent(face, (edge + 1) % 4));
            if (connects(level, diagonal, face)) mask |= 16 << edge;
        }
        return TownPavingConnections.canonical(mask);
    }

    private static boolean connects(BlockGetter level, BlockPos pos, Direction face) {
        if (!level.getBlockState(pos).is(ModBlocks.MINE_DESERT_WALL.get())) return false;
        BlockPos outside = pos.relative(face);
        // A projecting solid block interrupts the coplanar wall; terminate at the inner corner.
        return !level.getBlockState(outside).isCollisionShapeFullBlock(level, outside);
    }

    public static int phase(BlockPos pos, Direction face) {
        var frame = TerrainFaceConnections.frame(face);
        return coordinate(pos, frame.u()) + 2 * coordinate(pos, frame.v());
    }

    private static int coordinate(BlockPos pos, Direction direction) {
        var normal = direction.getNormal();
        int coordinate = pos.getX() * normal.getX() + pos.getY() * normal.getY() + pos.getZ() * normal.getZ();
        if (direction.getAxisDirection() == Direction.AxisDirection.NEGATIVE) coordinate--;
        return Math.floorMod(coordinate, 2);
    }

    public static int variant(BlockPos pos, Direction face) {
        long seed = pos.asLong() ^ (face.ordinal() * 0x9e3779b97f4a7c15L);
        seed = (seed ^ (seed >>> 30)) * 0xbf58476d1ce4e5b9L;
        seed = (seed ^ (seed >>> 27)) * 0x94d049bb133111ebL;
        return (int) Math.floorMod(seed ^ (seed >>> 31), 3);
    }
}
