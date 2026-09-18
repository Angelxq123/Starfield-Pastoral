package com.stardew.craft.mixin;

import com.stardew.craft.block.StardewBlockFluidProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Stops a flowing fluid before its replacement path can remove or drop a mod block. */
@Mixin(FlowingFluid.class)
public abstract class FlowingFluidStardewBlockProtectionMixin {
    @Inject(method = "canHoldFluid", at = @At("HEAD"), cancellable = true)
    private void stardewcraft$protectModBlocks(
            BlockGetter level,
            BlockPos pos,
            BlockState state,
            Fluid fluid,
            CallbackInfoReturnable<Boolean> result) {
        if (StardewBlockFluidProtection.blocksFlowReplacement(state)) {
            result.setReturnValue(false);
        }
    }
}
