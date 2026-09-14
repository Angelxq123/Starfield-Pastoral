package com.stardew.craft.mixin;

import com.stardew.craft.client.light.RingLightRenderer;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderer.class)
public abstract class RingLightEntityRendererMixin {
    @Inject(method = "getPackedLightCoords", at = @At("RETURN"), cancellable = true)
    private void stardewcraft$ringLight(Entity entity, float partialTick, CallbackInfoReturnable<Integer> cir) {
        Vec3 position = entity.getLightProbePosition(partialTick);
        cir.setReturnValue(RingLightRenderer.lightColor(position.x, position.y, position.z, cir.getReturnValueI()));
    }
}
