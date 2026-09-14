package com.stardew.craft.block.mastery;

import com.stardew.craft.block.decor.MapDecorStaticBlock;
import com.stardew.craft.block.shape.ModelVoxelShapeCache;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

/** Mastery blocks retain their saved state and placement orientation, sharing multipart behaviour. */
@SuppressWarnings("null")
public class TallMasteryBlock extends MapDecorStaticBlock {
    private final Direction baseFacing;

    public TallMasteryBlock(Properties properties, String modelId, Direction baseFacing) {
        super(properties, modelId);
        this.baseFacing = baseFacing;
    }

    @Override
    protected VoxelShape canonicalShape() {
        return ModelVoxelShapeCache.rotateY(super.canonicalShape(),
            ModelVoxelShapeCache.horizontalIndex(Direction.NORTH) - ModelVoxelShapeCache.horizontalIndex(baseFacing));
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection();
        return canPlaceAtFacing(context.getLevel(), context.getClickedPos(), facing, context)
            ? defaultBlockState().setValue(FACING, facing).setValue(PART, Part.MAIN) : null;
    }

    /** Existing mastery machines occupy MAIN and the cell directly above it. */
    public static BlockPos getMainPos(BlockPos pos, BlockState state) {
        return state.getValue(PART) == Part.EXTENSION ? pos.below() : pos;
    }
}
