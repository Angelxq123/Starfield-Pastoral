package com.stardew.craft.blockentity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** No server ticker or saved animation counters; motion is entirely client-side. */
public final class FloatingPlantBlockEntity extends BlockEntity {
    public FloatingPlantBlockEntity(BlockPos pos, BlockState state) { super(ModBlockEntities.FLOATING_PLANT.get(), pos, state); }
}
