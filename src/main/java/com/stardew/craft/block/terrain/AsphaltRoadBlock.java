package com.stardew.craft.block.terrain;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

/** A full road cube; the surface choice is saved, while curbs follow neighbors. */
public final class AsphaltRoadBlock extends Block {
    public static final MapCodec<AsphaltRoadBlock> CODEC = simpleCodec(AsphaltRoadBlock::new);

    public AsphaltRoadBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(TerrainVariants.ASPHALT, 0));
    }

    @Override public MapCodec<AsphaltRoadBlock> codec() { return CODEC; }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(TerrainVariants.ASPHALT);
    }

    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        return TerrainVariants.placement(defaultBlockState(), context);
    }
}
