package com.stardew.craft.client.gui.overnight;

/**
 * Shared fade math and explicit GUI depth contract for the shipping settlement screen.
 *
 * <p>Vanilla renders item models at z=150 and their count decorations at z=200. A normal
 * {@code GuiGraphics.fill(...)} uses z=0, so it cannot cover shipped item stacks even when the
 * fill call appears later in Java source. These layers keep the transition above every shipping
 * row while preserving the original date-plaque-over-black ordering.</p>
 */
final class ShippingMenuFadeTimeline {
    static final int INTRO_DURATION_MS = 3_500;
    static final int OUTRO_FADE_DURATION_MS = 350;
    static final int OUTRO_DATE_PAUSE_MS = 250;
    static final int SAVE_MARGIN_MS = 200;
    static final int SAVE_COMPLETE_PAUSE_MS = 500;
    static final int FINAL_OUTRO_DURATION_MS = 500;
    static final int MORNING_SOUND_DELAY_MS = 600;
    static final float DAY_PLAQUE_SPEED = 0.9F;

    static final int VANILLA_ITEM_DECORATION_Z = 200;
    static final int CONTENT_BLACKOUT_Z = 10_000;
    static final int OUTRO_FOREGROUND_Z = 11_000;
    static final int FINAL_BLACKOUT_Z = 20_000;

    private ShippingMenuFadeTimeline() {
    }

    static float introBlackAlpha(int remainingMs, int durationMs) {
        if (durationMs <= 0) {
            return 0.0F;
        }
        return clamp(remainingMs / (float) durationMs);
    }

    static float outroBlackAlpha(int remainingMs, int durationMs) {
        if (durationMs <= 0) {
            return 1.0F;
        }
        return 1.0F - clamp(remainingMs / (float) durationMs);
    }

    static int blackArgb(float alpha) {
        int alphaByte = Math.max(0, Math.min(255, Math.round(clamp(alpha) * 255.0F)));
        return alphaByte << 24;
    }

    static int advanceResultReveal(
            int remainingMs,
            int deltaMs,
            float speed,
            boolean awaitingSettlement
    ) {
        if (awaitingSettlement) {
            return remainingMs;
        }
        return remainingMs - scaledDelta(deltaMs, speed);
    }

    static int advanceBackgroundReveal(int remainingMs, int deltaMs, int speed) {
        return Math.max(0, remainingMs - scaledDelta(deltaMs, speed));
    }

    static int starScrollOffset(long elapsedMs, int tileWidth) {
        if (tileWidth <= 0) {
            return 0;
        }
        long pixels = Math.max(0L, elapsedMs) / 250L;
        return (int) (pixels % tileWidth);
    }

    static int fixedOutroDurationMs() {
        return OUTRO_FADE_DURATION_MS
                + OUTRO_DATE_PAUSE_MS
                + SAVE_MARGIN_MS
                + SAVE_COMPLETE_PAUSE_MS
                + FINAL_OUTRO_DURATION_MS;
    }

    static boolean hasSafeLayerOrdering() {
        return CONTENT_BLACKOUT_Z > VANILLA_ITEM_DECORATION_Z
            && OUTRO_FOREGROUND_Z > CONTENT_BLACKOUT_Z
            && FINAL_BLACKOUT_Z > OUTRO_FOREGROUND_Z;
    }

    private static float clamp(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    private static int scaledDelta(int deltaMs, float speed) {
        return Math.round(Math.max(0, deltaMs) * Math.max(0.0F, speed));
    }
}
