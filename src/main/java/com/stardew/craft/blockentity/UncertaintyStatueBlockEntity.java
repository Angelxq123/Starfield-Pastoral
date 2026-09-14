package com.stardew.craft.blockentity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/** Retained block entity id for existing saves; the statue now uses a baked Java model. */
public class UncertaintyStatueBlockEntity extends net.minecraft.world.level.block.entity.BlockEntity {
    public UncertaintyStatueBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.UNCERTAINTY_STATUE.get(), pos, state);
    }
}
