package com.stardew.craft.mixin;

import com.stardew.craft.client.light.RingLightRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Apply after Sodium's integer light cache, preserving fractional per-vertex brightness. */
@Pseudo
@Mixin(targets = {
        "net.caffeinemc.mods.sodium.client.model.light.smooth.SmoothLightPipeline",
        "net.caffeinemc.mods.sodium.client.model.light.flat.FlatLightPipeline"
}, remap = false)
public abstract class SodiumRingLightPipelineMixin {
    @Inject(method = "calculate", at = @At("RETURN"))
    private void stardewcraft$ringLight(@Coerce Object quad, BlockPos pos, @Coerce Object lightData,
                                      Direction cullFace, Direction lightFace, boolean shade,
                                      boolean enhanced, CallbackInfo ci) {
        SodiumRingLightQuadAccessor vertices = (SodiumRingLightQuadAccessor) quad;
        int[] lightmap = ((SodiumRingLightDataAccessor) lightData).stardewcraft$getLightmap();
        for (int i = 0; i < 4; i++) {
            lightmap[i] = RingLightRenderer.lightColor(pos.getX() + vertices.stardewcraft$getX(i),
                    pos.getY() + vertices.stardewcraft$getY(i), pos.getZ() + vertices.stardewcraft$getZ(i), lightmap[i]);
        }
    }
}
