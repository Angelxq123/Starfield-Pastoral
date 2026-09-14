package com.stardew.craft.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.stardew.craft.client.gui.common.StardewGuiViewport;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Minecraft.class)
public abstract class StardewGuiTickMixin {
    // Screen.tick is called from a compiler-generated lambda inside Minecraft.tick.
    @WrapOperation(method = "*", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/Screen;tick()V"))
    private void stardewcraft$screenTick(Screen screen, Operation<Void> original) {
        var previous = StardewGuiViewport.enterForScreen(screen, Minecraft.getInstance().getWindow());
        try { original.call(screen); }
        finally { StardewGuiViewport.restore(previous); }
    }
}
