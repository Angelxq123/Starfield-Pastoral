package com.stardew.craft.block.decor;

import net.minecraft.core.BlockPos;

/** Continuous client-time motion; no animation state is written to the world. */
public final class FloatingPlantMotion {
    private FloatingPlantMotion() {}

    public static int period(NaturalDecorKind kind) {
        return kind == NaturalDecorKind.WATER_LILY ? 112 : 96;
    }

    public static double phase(NaturalDecorKind kind, BlockPos pos, long tick, float partialTick) {
        long hash = pos.asLong() * 0x9E3779B97F4A7C15L;
        hash ^= hash >>> 32;
        double offset = (hash & 0xffffL) / 65536.0;
        // Reduce before converting to floating point, retaining smooth sub-ticks in old worlds.
        return 2 * Math.PI * ((Math.floorMod(tick, period(kind)) + partialTick) / period(kind) + offset);
    }

    public static double bob(double phase) { return .025 + .050 * Math.sin(phase); }
    public static float pitch(double phase) { return (float) (2.8 * Math.sin(phase)); }
    public static float roll(double phase) { return (float) (3.6 * Math.cos(phase)); }
    public static double rippleProgress(double phase, int ring) {
        double cycle = phase / (2 * Math.PI) + ring * .5;
        return cycle - Math.floor(cycle);
    }
    public static float rippleScale(double progress) { return (float) (.9 + .3 * progress); }
    public static float rippleAlpha(double progress) {
        double sine = Math.sin(Math.PI * progress);
        return (float) (.55 * sine * sine);
    }
}
