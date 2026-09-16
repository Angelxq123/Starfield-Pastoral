package com.stardew.craft.blockentity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/** The saved block state is sufficient. No server animation ticker or changing NBT. */
public final class DoubleSwingBlockEntity extends BlockEntity {
    public DoubleSwingBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DOUBLE_SWING.get(), pos, state);
    }

    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition.getX() - 3, worldPosition.getY(), worldPosition.getZ() - 3,
                worldPosition.getX() + 4, worldPosition.getY() + 5.125, worldPosition.getZ() + 4);
    }
}
