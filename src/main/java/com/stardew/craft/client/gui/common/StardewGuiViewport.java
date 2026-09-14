package com.stardew.craft.client.gui.common;

import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/** Design units are independent of the user's GUI setting. Window overrides are scoped compatibility for native widgets. */
public final class StardewGuiViewport {
    public static final double REFERENCE_SCALE = 4.0;
    private static final ThreadLocal<GuiLayoutMath.Viewport> ACTIVE = new ThreadLocal<>();

    private StardewGuiViewport() {}

    /** Shared art helpers also render in the HUD, outside the screen canvas. */
    public static float renderScale() {
        return active() == null ? (float) Minecraft.getInstance().getWindow().getGuiScale()
                : (float) REFERENCE_SCALE;
    }

    public static boolean supports(Screen screen) {
        if (screen == null) return false;
        if (screen instanceof StardewGuiContentSize) return true;
        String name = screen.getClass().getName();
        return name.startsWith("com.stardew.craft.client.gui.")
                || name.startsWith("com.stardew.craft.communitycenter.client.")
                || name.startsWith("com.stardew.craft.joja.client.");
    }

    public static GuiLayoutMath.Viewport forScreen(Screen screen, Window window) {
        GuiLayoutMath.Viewport previous = ACTIVE.get();
        ACTIVE.remove();
        try {
            int width = 480, height = 270;
            if (screen instanceof StardewGuiContentSize content) {
                width = Math.max(width, content.minimumCanvasWidth());
                height = Math.max(height, content.minimumCanvasHeight());
            }
            return GuiLayoutMath.viewport(window.getWidth(), window.getHeight(), window.getGuiScale(), width, height);
        } finally {
            restore(previous);
        }
    }

    public static GuiLayoutMath.Viewport active() { return ACTIVE.get(); }

    public static GuiLayoutMath.Viewport enterForScreen(Screen screen, Window window) {
        return enter(supports(screen) ? forScreen(screen, window) : null);
    }

    public static GuiLayoutMath.Viewport enter(GuiLayoutMath.Viewport layout) {
        GuiLayoutMath.Viewport previous = ACTIVE.get();
        restore(layout);
        return previous;
    }

    public static void restore(GuiLayoutMath.Viewport layout) {
        if (layout == null) ACTIVE.remove();
        else ACTIVE.set(layout);
    }
}
