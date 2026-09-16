package com.stardew.craft.block.decor;

import com.stardew.craft.blockentity.SebastianComputerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/** A persistent, placeable workstation. Extensions use the shared furniture lifecycle. */
public final class SebastianComputerBlock extends MapDecorStaticBlock implements EntityBlock {
    public SebastianComputerBlock(Properties properties) {
        super(properties, "stardewcraft:block/furniture/sebastian_computer_empty",
                1, 0, 0, 24, 15, 25);
    }

    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.ENTITYBLOCK_ANIMATED; }

    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(PART) == Part.MAIN ? new SebastianComputerBlockEntity(pos, state) : null;
    }

    public AABB renderBounds(BlockState state, BlockPos pos) {
        return rotateShapeForFacing(canonicalShape(), state.getValue(FACING)).bounds().move(pos);
    }
}
