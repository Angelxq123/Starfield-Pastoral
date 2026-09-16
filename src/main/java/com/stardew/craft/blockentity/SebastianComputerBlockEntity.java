package com.stardew.craft.blockentity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** The furniture has no independent animation clock; the bound NPC event owns playback. */
public final class SebastianComputerBlockEntity extends BlockEntity {
    public SebastianComputerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SEBASTIAN_COMPUTER.get(), pos, state);
    }
}
