package com.stardew.craft.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.platform.Window;
import com.stardew.craft.client.gui.common.StardewGuiViewport;
import com.stardew.craft.client.gui.common.GuiScissorMath;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(GuiGraphics.class)
public abstract class StardewGuiScissorMixin {
    @WrapMethod(method = "enableScissor")
    private void stardewcraft$pushClip(int left, int top, int right, int bottom, Operation<Void> original) {
        var layout = StardewGuiViewport.active();
        if (layout == null) {
            original.call(left, top, right, bottom);
            return;
        }
        var pose = ((GuiGraphics) (Object) this).pose().last().pose();
        var clip = GuiScissorMath.framebuffer(pose, layout.windowScale(), left, top, right, bottom);
        original.call(clip.left(), clip.top(), clip.right(), clip.bottom());
    }

    @WrapMethod(method = "containsPointInScissor")
    private boolean stardewcraft$clipHit(int x, int y, Operation<Boolean> original) {
        var layout = StardewGuiViewport.active();
        if (layout == null) return original.call(x, y);
        var pose = ((GuiGraphics) (Object) this).pose().last().pose();
        return original.call(GuiScissorMath.pointX(pose, layout.windowScale(), x, y),
                GuiScissorMath.pointY(pose, layout.windowScale(), x, y));
    }

    @WrapOperation(method = "applyScissor", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/platform/Window;getGuiScale()D"))
    private double stardewcraft$physicalScissor(Window window, Operation<Double> original) {
        var layout = StardewGuiViewport.active();
        // Every active stack entry is already in physical pixels, including a parent
        // restored by disableScissor. Do not apply the current pose a second time.
        return layout == null ? original.call(window) : 1.0;
    }
}
