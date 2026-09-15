package com.stardew.craft.block.decor;

import javax.annotation.Nonnull;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** Static Grandpa's Shrine; placement and its three broad collision boxes share one profile. */
public final class ShrineDecorBlock extends MapDecorStaticBlock {
    public ShrineDecorBlock(Properties properties, String modelId) {
        super(properties, modelId);
    }

    @Override
    public void onRemove(@Nonnull BlockState state, @Nonnull Level level, @Nonnull BlockPos pos,
                         @Nonnull BlockState newState, boolean moving) {
        if (!state.is(newState.getBlock()) && state.getValue(PART) == Part.EXTENSION && !level.isClientSide) {
            BlockPos main = findMainPos(level, pos, state);
            if (main != null && level.getBlockState(main).is(this)) {
                if (!dropsSuppressed()) popResource(level, main, new ItemStack(this));
                // Its footprint starts west of MAIN. Remove MAIN before any other
                // extension, so explosions/automation cannot drop once per cell.
                runWithDropsSuppressed(() -> level.removeBlock(main, false));
            }
        }
        super.onRemove(state, level, pos, newState, moving);
    }
}
