package com.stardew.craft.blockentity;

import com.stardew.craft.block.decor.IceCreamStandBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/** No ticker or seasonal block replacement; only the main cell renders the arrangement. */
public final class IceCreamStandBlockEntity extends BlockEntity {
    public IceCreamStandBlockEntity(BlockPos pos, BlockState state) { super(ModBlockEntities.ICE_CREAM_STAND.get(), pos, state); }
    public AABB getRenderBoundingBox() { return ((IceCreamStandBlock) getBlockState().getBlock()).renderBounds(getBlockState(), worldPosition); }
}
