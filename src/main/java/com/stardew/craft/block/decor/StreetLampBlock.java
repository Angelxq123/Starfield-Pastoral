package com.stardew.craft.block.decor;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/** Three-cell lamp: the head alone emits world light; emissive faces live in the model. */
public final class StreetLampBlock extends MapDecorStaticBlock {
    public static final BooleanProperty LIGHT_SOURCE = BooleanProperty.create("light_source");

    public StreetLampBlock(Properties properties) {
        super(properties.lightLevel(state -> state.getValue(LIGHT_SOURCE) ? 15 : 0),
            "stardewcraft:decor/common/street_lamp_placed");
        registerDefaultState(defaultBlockState().setValue(LIGHT_SOURCE, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(LIGHT_SOURCE);
    }

    @Override
    protected BlockState extensionState(BlockState mainState, BlockPos offset) {
        return super.extensionState(mainState, offset).setValue(LIGHT_SOURCE, offset.getY() == 2);
    }
}
