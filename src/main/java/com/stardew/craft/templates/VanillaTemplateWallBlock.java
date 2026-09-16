package com.stardew.craft.templates;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

public final class VanillaTemplateWallBlock extends WallBlock implements EntityBlock, TemplateBlock {
    public VanillaTemplateWallBlock(BlockBehaviour.Properties properties) { super(properties); }
    @Override public TemplateShape templateShape() { return TemplateShape.WALL; }
    @Nullable @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TemplateBlockEntity(pos, state);
    }
}
