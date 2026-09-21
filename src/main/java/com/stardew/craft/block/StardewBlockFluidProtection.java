package com.stardew.craft.block;

import com.stardew.craft.StardewCraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.LiquidBlockContainer;
import net.minecraft.world.level.block.state.BlockState;

/** Shared rule preventing flowing fluids from replacing StardewCraft content. */
public final class StardewBlockFluidProtection {
    private StardewBlockFluidProtection() {
    }

    public static boolean blocksFlowReplacement(BlockState state) {
        if (!state.getFluidState().isEmpty() || state.getBlock() instanceof LiquidBlockContainer) {
            return false;
        }
        return StardewCraft.MODID.equals(
                BuiltInRegistries.BLOCK.getKey(state.getBlock()).getNamespace());
    }
}
