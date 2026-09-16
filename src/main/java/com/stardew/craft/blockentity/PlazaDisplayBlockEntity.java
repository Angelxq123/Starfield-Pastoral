package com.stardew.craft.blockentity;

import com.stardew.craft.block.decor.PlazaDisplayBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/** No ticker or seasonal block replacement; only the main cell renders the arrangement. */
public final class PlazaDisplayBlockEntity extends BlockEntity {
    public PlazaDisplayBlockEntity(BlockPos pos, BlockState state) { super(ModBlockEntities.PLAZA_DISPLAY.get(), pos, state); }
    public AABB getRenderBoundingBox() { return ((PlazaDisplayBlock) getBlockState().getBlock()).renderBounds(getBlockState(), worldPosition); }
}
