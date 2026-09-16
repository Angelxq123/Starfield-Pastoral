package com.stardew.craft.blockentity;

import com.stardew.craft.block.decor.ParkedVehicleBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/** No ticker: appearance follows the client season without changing placed block identity. */
public final class ParkedVehicleBlockEntity extends BlockEntity {
    public ParkedVehicleBlockEntity(BlockPos pos, BlockState state) { super(ModBlockEntities.PARKED_VEHICLE.get(), pos, state); }
    public AABB getRenderBoundingBox() { return ((ParkedVehicleBlock) getBlockState().getBlock()).renderBounds(getBlockState(), worldPosition); }
}
