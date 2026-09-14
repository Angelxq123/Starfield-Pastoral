package com.stardew.craft.block.decor;

import com.stardew.craft.blockentity.PillarGeoBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

@SuppressWarnings("null")
public class PillarGeoDecorBlock extends MapDecorStaticBlock implements EntityBlock {

    public PillarGeoDecorBlock(Properties properties, String modelId) {
        super(properties, modelId, 0, 0, 0, 16, 48, 16);
    }

    @Override
    public RenderShape getRenderShape(@Nonnull BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(@Nonnull BlockPos pos, @Nonnull BlockState state) {
        if (state.getValue(PART) != Part.MAIN) {
            return null;
        }
        return new PillarGeoBlockEntity(pos, state);
    }
}
