package com.stardew.craft.block.terrain;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;

/** Native farmland shape, moisture, crop and hydration hooks, with authored seasonal surfaces. */
public class TerrainFarmlandBlock extends FarmBlock {
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
                ? defaultBlockState() : TerrainSoils.substrate(defaultBlockState()).defaultBlockState();
    }

    public static final class Infertile extends TerrainFarmlandBlock {
        public static final MapCodec<FarmBlock> CODEC = simpleCodec(Infertile::new);
        public Infertile(Properties properties) { super(properties); }
        @Override public MapCodec<FarmBlock> codec() { return CODEC; }
    }

    public static final class Sandy extends TerrainFarmlandBlock {
        public static final MapCodec<FarmBlock> CODEC = simpleCodec(Sandy::new);
        public Sandy(Properties properties) { super(properties); }
        @Override public MapCodec<FarmBlock> codec() { return CODEC; }
    }
}
