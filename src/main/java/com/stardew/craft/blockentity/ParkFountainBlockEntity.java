package com.stardew.craft.blockentity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/** The saved block state is sufficient. No server animation ticker or changing NBT. */
public final class ParkFountainBlockEntity extends BlockEntity {
    public ParkFountainBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PARK_FOUNTAIN.get(), pos, state);
    }

    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition.getX() - 2, worldPosition.getY(), worldPosition.getZ() - 2,
                worldPosition.getX() + 3, worldPosition.getY() + 3.25, worldPosition.getZ() + 3);
    }
}
