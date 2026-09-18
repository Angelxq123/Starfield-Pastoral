package com.stardew.craft.mixin;

import com.stardew.craft.client.interior.TownDoorRenderContext;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Fabulous auxiliary targets cannot share the main target's portal stencil attachment. */
@Mixin(Minecraft.class)
public abstract class TownDoorMinecraftMixin {
    @Inject(method = "useShaderTransparency", at = @At("HEAD"), cancellable = true)
    private static void stardewcraft$drawNestedViewIntoMainTarget(CallbackInfoReturnable<Boolean> cir) {
        if (TownDoorRenderContext.isRendering()) cir.setReturnValue(false);
    }
}
