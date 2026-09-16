package com.stardew.craft.mixin;

import com.mojang.blaze3d.platform.Window;
import com.stardew.craft.client.gui.common.StardewGuiViewport;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The override exists only inside a Stardew screen operation; user settings are untouched. */
@Mixin(Window.class)
public abstract class StardewGuiWindowMixin {
    @Inject(method = "getGuiScale", at = @At("HEAD"), cancellable = true)
    private void stardewcraft$referenceScale(CallbackInfoReturnable<Double> cir) {
        if (StardewGuiViewport.active() != null) cir.setReturnValue(StardewGuiViewport.REFERENCE_SCALE);
    }

    @Inject(method = "getGuiScaledWidth", at = @At("HEAD"), cancellable = true)
    private void stardewcraft$canvasWidth(CallbackInfoReturnable<Integer> cir) {
        var layout = StardewGuiViewport.active();
        if (layout != null) cir.setReturnValue(layout.width());
    }

    @Inject(method = "getGuiScaledHeight", at = @At("HEAD"), cancellable = true)
    private void stardewcraft$canvasHeight(CallbackInfoReturnable<Integer> cir) {
        var layout = StardewGuiViewport.active();
        if (layout != null) cir.setReturnValue(layout.height());
    }
}
