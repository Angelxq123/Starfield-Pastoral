package com.stardew.craft.templates;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.StairsShape;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class TemplateShapeCache {
    private static final Map<Key, VoxelShape> CACHE = new ConcurrentHashMap<>();

    static VoxelShape get(TemplateShape shape, BlockState state) {
        return get(shape, state, 15);
    }

    static VoxelShape get(TemplateShape shape, BlockState state, int edges) {
        if (shape == TemplateShape.GRID_WINDOW) return GridWindowTemplateBlock.outline(state);
        if (shape == TemplateShape.BALCONY_RAILING) return BalconyRailingProfile.shape(state);
        if (shape == TemplateShape.CHIMNEY) return ChimneyTemplateBlock.outline(state);
        Direction facing = state.getValue(MaterialTemplateBlock.FACING);
        boolean flipped = state.getValue(MaterialTemplateBlock.FLIPPED);
        StairsShape roofShape = state.hasProperty(SmartRoofTemplateBlock.ROOF_SHAPE)
                ? state.getValue(SmartRoofTemplateBlock.ROOF_SHAPE)
                : StairsShape.STRAIGHT;
        int layers = state.hasProperty(SnowLayerTemplateBlock.LAYERS) ? state.getValue(SnowLayerTemplateBlock.LAYERS) : 1;
        return CACHE.computeIfAbsent(new Key(shape, facing, flipped, roofShape, layers, SmartRidgeTemplateBlock.connectionMask(state), ConnectedFacadeTemplateBlock.connections(state),
                !state.hasProperty(RoofTemplateBlock.FILLED) || state.getValue(RoofTemplateBlock.FILLED), edges,
                state.hasProperty(HalfStairsTemplateBlock.MIRRORED) && state.getValue(HalfStairsTemplateBlock.MIRRORED)), TemplateShapeCache::build);
    }

    private static VoxelShape build(Key key) {
        VoxelShape result = Shapes.empty();
        int turns = key.shape == TemplateShape.ROOF_RIDGE ? 0 : turnsFrom(key.shape.baseFacing(), key.facing);
        int localEdges = ((key.edges >> turns) | (key.edges << (4-turns))) & 15;
        var boxes = key.shape.meshKind() == TemplateShape.MeshKind.ROOF
                ? key.shape.roofForm().collisionBoxes(key.roofShape, key.ridgeMask, key.filled, localEdges)
                : key.shape.collisionBoxes(key.roofShape, key.layers);
        if (key.shape.isCompositeWall() || key.shape.isWindow()) {
            boxes = new java.util.ArrayList<>(FacadeTemplateGeometry.frame(key.shape,key.connections));
            if (key.filled || key.shape.isWindow()) boxes.addAll(FacadeTemplateGeometry.fill(key.shape,key.connections));
        }
        if (key.shape == TemplateShape.ROUND_WINDOW) {
            boxes = new java.util.ArrayList<>(RoundWindowProfile.frame());
            boxes.addAll(RoundWindowProfile.glass());
            if (key.filled) boxes.addAll(RoundWindowProfile.fill());
        }
        for (TemplateBox box : HalfStairsTemplateBlock.boxes(boxes, key.mirrored)) {
            double minX = box.minX() / 16D;
            double minY = box.minY() / 16D;
            double minZ = box.minZ() / 16D;
            double maxX = box.maxX() / 16D;
            double maxY = box.maxY() / 16D;
            double maxZ = box.maxZ() / 16D;

            if (key.flipped) {
                double oldMinY = minY;
                minY = 1D - maxY;
                maxY = 1D - oldMinY;
            }

            for (int turn = 0; turn < turns; turn++) {
                double oldMinX = minX;
                double oldMaxX = maxX;
                minX = 1D - maxZ;
                maxX = 1D - minZ;
                minZ = oldMinX;
                maxZ = oldMaxX;
            }
            result = Shapes.or(result, Shapes.box(minX, minY, minZ, maxX, maxY, maxZ));
        }
        return result.optimize();
    }

    public static int turnsFrom(Direction source, Direction target) {
        if (!source.getAxis().isHorizontal() || !target.getAxis().isHorizontal()) {
            return 0;
        }
        int turns = 0;
        Direction direction = source;
        while (direction != target && turns < 4) {
            direction = direction.getClockWise();
            turns++;
        }
        return turns;
    }

    private record Key(TemplateShape shape, Direction facing, boolean flipped, StairsShape roofShape, int layers, int ridgeMask, int connections, boolean filled, int edges, boolean mirrored) {
    }

    private TemplateShapeCache() {
    }
}
