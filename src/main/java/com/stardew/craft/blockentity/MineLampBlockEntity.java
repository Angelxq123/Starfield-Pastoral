package com.stardew.craft.blockentity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Static lamp; the block state owns the theme and switch, so no server ticker or custom NBT is needed. */
public final class MineLampBlockEntity extends BlockEntity {
    public MineLampBlockEntity(BlockPos pos, BlockState state) { super(ModBlockEntities.MINE_LAMP.get(), pos, state); }
}
