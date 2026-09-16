package com.stardew.craft.blockentity;

import com.stardew.craft.block.decor.BlacksmithVentilatorBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/** No ticker or seasonal block replacement; only the main cell renders the arrangement. */
public final class BlacksmithVentilatorBlockEntity extends BlockEntity {
    public BlacksmithVentilatorBlockEntity(BlockPos pos, BlockState state) { super(ModBlockEntities.BLACKSMITH_VENTILATOR.get(), pos, state); }
    public AABB getRenderBoundingBox() { return ((BlacksmithVentilatorBlock) getBlockState().getBlock()).renderBounds(getBlockState(), worldPosition); }
}
