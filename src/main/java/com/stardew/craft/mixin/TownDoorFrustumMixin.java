package com.stardew.craft.mixin;

import com.stardew.craft.client.interior.TownDoorRenderContext;
import net.minecraft.client.renderer.culling.Frustum;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Restricts the nested world to the doorway cone and its fixed-interior destination. */
@Mixin(Frustum.class)
public abstract class TownDoorFrustumMixin {
    @Inject(method = "cubeInFrustum", at = @At("HEAD"), cancellable = true)
    private void stardewcraft$cullOutsideDoorway(double minX, double minY, double minZ,
                                                  double maxX, double maxY, double maxZ,
                                                  CallbackInfoReturnable<Boolean> cir) {
        if (TownDoorRenderContext.isWorldBoxOutsidePortalView(minX, minY, minZ, maxX, maxY, maxZ)) {
            cir.setReturnValue(false);
        }
    }
}
