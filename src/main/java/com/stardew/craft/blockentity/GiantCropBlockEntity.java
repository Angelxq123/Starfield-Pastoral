package com.stardew.craft.blockentity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Retains the old save ID; new giant crops use static models and create no block entity. */
public class GiantCropBlockEntity extends BlockEntity {
    public GiantCropBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.GIANT_CROP.get(), pos, state);
    }
}
