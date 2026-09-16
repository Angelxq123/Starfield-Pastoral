package com.stardew.craft.blockentity;

import com.stardew.craft.block.decor.JojaBillboardBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/** No ticker or seasonal block replacement; only the main cell renders the arrangement. */
public final class JojaBillboardBlockEntity extends BlockEntity {
    public JojaBillboardBlockEntity(BlockPos pos, BlockState state) { super(ModBlockEntities.JOJA_BILLBOARD.get(), pos, state); }
    public AABB getRenderBoundingBox() { return ((JojaBillboardBlock) getBlockState().getBlock()).renderBounds(getBlockState(), worldPosition); }
}
