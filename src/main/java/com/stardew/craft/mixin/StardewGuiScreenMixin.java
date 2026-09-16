package com.stardew.craft.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.stardew.craft.client.gui.common.StardewGuiViewport;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(Screen.class)
public abstract class StardewGuiScreenMixin {
    @WrapMethod(method = {"init(Lnet/minecraft/client/Minecraft;II)V", "resize"})
    private void stardewcraft$layout(Minecraft minecraft, int width, int height, Operation<Void> original) {
        if (!StardewGuiViewport.supports((Screen) (Object) this)) {
            var previous = StardewGuiViewport.enter(null);
            try {
                // A mod screen may open a vanilla screen from inside an input callback.
                original.call(minecraft, previous == null ? width : minecraft.getWindow().getGuiScaledWidth(),
                        previous == null ? height : minecraft.getWindow().getGuiScaledHeight());
            } finally {
                StardewGuiViewport.restore(previous);
            }
            return;
        }
        var screen = (Screen) (Object) this;
        var layout = StardewGuiViewport.forScreen(screen, minecraft.getWindow());
        var previous = StardewGuiViewport.enter(layout);
        try {
            original.call(minecraft, layout.width(), layout.height());
            // Text-dependent pages can only report intrinsic bounds after their first init.
            var fitted = StardewGuiViewport.forScreen(screen, minecraft.getWindow());
            if (fitted.width() != screen.width || fitted.height() != screen.height) {
                StardewGuiViewport.enter(fitted);
                original.call(minecraft, fitted.width(), fitted.height());
            }
        } finally {
            StardewGuiViewport.restore(previous);
        }
    }

    @WrapMethod(method = "rebuildWidgets")
    private void stardewcraft$rebuild(Operation<Void> original) {
        var previous = StardewGuiViewport.active();
        if (StardewGuiViewport.supports((Screen) (Object) this)) {
            var screen = (Screen) (Object) this;
            var layout = StardewGuiViewport.forScreen(screen, Minecraft.getInstance().getWindow());
            screen.width = layout.width();
            screen.height = layout.height();
            StardewGuiViewport.enter(layout);
        } else {
            StardewGuiViewport.restore(null);
        }
        try {
            original.call();
            var screen = (Screen) (Object) this;
            if (StardewGuiViewport.supports(screen)) {
                var fitted = StardewGuiViewport.forScreen(screen, Minecraft.getInstance().getWindow());
                if (fitted.width() != screen.width || fitted.height() != screen.height) {
                    screen.width = fitted.width();
                    screen.height = fitted.height();
                    StardewGuiViewport.enter(fitted);
                    original.call();
                }
            }
        } finally {
            StardewGuiViewport.restore(previous);
        }
    }
}
