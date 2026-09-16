package com.stardew.craft.templates;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

public final class VanillaTemplateLadderBlock extends LadderBlock implements EntityBlock, TemplateBlock {
    public VanillaTemplateLadderBlock(BlockBehaviour.Properties properties) { super(properties); }
    @Override public TemplateShape templateShape() { return TemplateShape.LADDER; }
    @Nullable @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TemplateBlockEntity(pos, state);
    }
}
