package com.stardew.craft.mixin;

import com.stardew.craft.client.light.RingLightRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelRenderer.class)
public abstract class RingLightLevelRendererMixin {
    @Inject(method = "getLightColor(Lnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;)I",
            at = @At("RETURN"), cancellable = true)
    private static void stardewcraft$ringLight(BlockAndTintGetter level, BlockState state, BlockPos pos,
                                              CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(RingLightRenderer.lightColor(pos.getX() + 0.5, pos.getY() + 0.5,
                pos.getZ() + 0.5, cir.getReturnValueI()));
    }
}
