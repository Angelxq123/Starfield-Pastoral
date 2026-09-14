package com.stardew.craft.client.gui.common;

/** Pure layout arithmetic, independent of Minecraft's renderer and the selected language. */
public final class GuiLayoutMath {
    private GuiLayoutMath() {}

    public record Viewport(int width, int height, double scale, double x, double y, double windowScale) {
        public double mouseX(double screenX) { return (screenX - x) / scale; }
        public double mouseY(double screenY) { return (screenY - y) / scale; }
        public double windowMouseX(double rawX, int windowWidth) {
            return rawMouseX(rawX, windowWidth) * windowWidth / width;
        }
        public double windowMouseY(double rawY, int windowHeight) {
            return rawMouseY(rawY, windowHeight) * windowHeight / height;
        }
        public double rawMouseX(double rawX, int windowWidth) {
            return mouseX(rawX * (width * scale + 2 * x) / windowWidth);
        }
        public double rawMouseY(double rawY, int windowHeight) {
            return mouseY(rawY * (height * scale + 2 * y) / windowHeight);
        }
        public double windowDeltaX(double delta) { return delta * (width * scale + 2 * x) / (width * scale); }
        public double windowDeltaY(double delta) { return delta * (height * scale + 2 * y) / (height * scale); }
    }

    public static Viewport viewport(int pixelsWide, int pixelsHigh, double windowScale) {
        return viewport(pixelsWide, pixelsHigh, windowScale, 480, 270);
    }

    public static Viewport viewport(int pixelsWide, int pixelsHigh, double windowScale, int minimumWidth, int minimumHeight) {
        int minWidth = Math.max(480, minimumWidth), minHeight = Math.max(270, minimumHeight);
        double pixelsPerUnit = Math.min(4.0,
                Math.min(Math.max(1, pixelsWide) / (double) minWidth, Math.max(1, pixelsHigh) / (double) minHeight));
        int width = Math.max(minWidth, (int) Math.floor(pixelsWide / pixelsPerUnit));
        int height = Math.max(minHeight, (int) Math.floor(pixelsHigh / pixelsPerUnit));
        // GameRenderer's projection uses physical size / GUI scale, not the rounded
        // Window.getGuiScaledWidth/Height. Rounding here makes the image drift at scale 3.
        double projectedWidth = Math.max(1, pixelsWide) / windowScale;
        double projectedHeight = Math.max(1, pixelsHigh) / windowScale;
        double scale = pixelsPerUnit / windowScale;
        return new Viewport(width, height, scale, (projectedWidth - width * scale) / 2,
                (projectedHeight - height * scale) / 2, windowScale);
    }

    public static float fitTextScale(float preferred, float textWidth, float textHeight, float boxWidth, float boxHeight) {
        return fitScale(preferred, textWidth, textHeight, boxWidth, boxHeight);
    }

    /** Uniform fit for text or art. A preferred minimum must never override available space. */
    public static float fitScale(float preferred, float contentWidth, float contentHeight, float boxWidth, float boxHeight) {
        if (boxWidth <= 0 || boxHeight <= 0) return 0;
        return Math.max(0, Math.min(preferred, Math.min(boxWidth / Math.max(1, contentWidth),
                boxHeight / Math.max(1, contentHeight))));
    }
}
