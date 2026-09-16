package com.stardew.craft.templates;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockSetType;

public final class VanillaTemplateTrapDoorBlock extends TrapDoorBlock implements EntityBlock, TemplateBlock {
    private final TemplateShape shape;
    public VanillaTemplateTrapDoorBlock(TemplateShape shape, BlockBehaviour.Properties properties) {
        super(shape == TemplateShape.IRON_TRAPDOOR ? BlockSetType.IRON : BlockSetType.OAK, properties);
        this.shape = shape;
    }
    @Override public TemplateShape templateShape() { return shape; }
    @Nullable @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TemplateBlockEntity(pos, state);
    }
}
