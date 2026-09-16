package com.stardew.craft.blockentity;

import com.stardew.craft.block.decor.PlaygroundBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/** Static assembly: no tick or extra saved data. */
public final class PlaygroundBlockEntity extends BlockEntity {
    public PlaygroundBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PLAYGROUND.get(), pos, state);
    }

    public AABB getRenderBoundingBox() {
        int radius = ((PlaygroundBlock) getBlockState().getBlock()).isSlide() ? 5 : 3;
        return new AABB(worldPosition.getX() - radius, worldPosition.getY(), worldPosition.getZ() - radius,
                worldPosition.getX() + radius + 1, worldPosition.getY() + 5, worldPosition.getZ() + radius + 1);
    }
}
