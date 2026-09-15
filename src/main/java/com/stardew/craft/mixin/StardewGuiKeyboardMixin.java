package com.stardew.craft.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.stardew.craft.client.gui.common.StardewGuiViewport;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(KeyboardHandler.class)
public abstract class StardewGuiKeyboardMixin {
    @WrapMethod(method = "keyPress")
    private void stardewcraft$key(long window, int key, int scanCode, int action, int modifiers, Operation<Void> original) {
        Minecraft minecraft = Minecraft.getInstance();
        var previous = StardewGuiViewport.enterForScreen(minecraft.screen, minecraft.getWindow());
        try {
            original.call(window, key, scanCode, action, modifiers);
            if (key == 258 && action != 0) com.stardew.craft.client.gui.common.StardewReadingZoom.revealFocus(minecraft.screen);
        }
        finally { StardewGuiViewport.restore(previous); }
    }

    @WrapMethod(method = "charTyped")
    private void stardewcraft$character(long window, int codePoint, int modifiers, Operation<Void> original) {
        Minecraft minecraft = Minecraft.getInstance();
        var previous = StardewGuiViewport.enterForScreen(minecraft.screen, minecraft.getWindow());
        try { original.call(window, codePoint, modifiers); }
        finally { StardewGuiViewport.restore(previous); }
    }
}
