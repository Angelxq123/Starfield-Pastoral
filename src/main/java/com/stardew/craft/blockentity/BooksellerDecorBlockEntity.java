package com.stardew.craft.blockentity;

import com.stardew.craft.block.decor.BooksellerDecorBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

public final class BooksellerDecorBlockEntity extends BlockEntity {
    public BooksellerDecorBlockEntity(BlockPos pos, BlockState state) { super(ModBlockEntities.BOOKSELLER_DECOR.get(), pos, state); }
    public AABB getRenderBoundingBox() { return ((BooksellerDecorBlock) getBlockState().getBlock()).renderBounds(getBlockState(), worldPosition); }
}
