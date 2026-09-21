package com.stardew.craft.blockentity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class SupplyCrateBlockEntity extends BlockEntity {
    // Client-only visual state; no server ticker or persisted animation clock.
    public double lastHitTick = -100;

    public SupplyCrateBlockEntity(BlockPos pos, BlockState state) { super(ModBlockEntities.SUPPLY_CRATE.get(), pos, state); }
}
