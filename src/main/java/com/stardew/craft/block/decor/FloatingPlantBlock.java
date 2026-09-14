package com.stardew.craft.block.decor;

import com.stardew.craft.blockentity.FloatingPlantBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class FloatingPlantBlock extends NaturalPlantBlock implements EntityBlock {
    public FloatingPlantBlock(Properties properties, NaturalDecorKind kind) { super(properties, kind); }
    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.ENTITYBLOCK_ANIMATED; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new FloatingPlantBlockEntity(pos, state); }
}
