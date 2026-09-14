package com.stardew.craft.block.terrain;

import com.mojang.serialization.MapCodec;
import com.stardew.craft.block.ModBlocks;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;

/** Native farmland shape, moisture, crop and hydration hooks, with authored seasonal surfaces. */
public final class TerrainFarmlandBlock extends FarmBlock {
    public static final MapCodec<FarmBlock> CODEC = simpleCodec(TerrainFarmlandBlock::new);

    public TerrainFarmlandBlock(Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<FarmBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().canSurvive(context.getLevel(), context.getClickedPos())
                ? defaultBlockState() : ModBlocks.DIRT.get().defaultBlockState();
    }
}
