package com.stardew.craft.block.decor;

/** Continuous motion in seconds, shared by preview and runtime (no frame-based texture animation). */
public final class BirdSpringRiderMotion {
    private BirdSpringRiderMotion() {}
    public static net.minecraft.world.phys.Vec3 surface(net.minecraft.core.BlockPos main, net.minecraft.core.Direction facing,
            long ticks, float partialTick) {
        double a = Math.toRadians(angle((ticks + partialTick) / 20.0));
        double y = 8 + 9 * Math.cos(a) - .5 * Math.sin(a);
        double z = 15 + 9 * Math.sin(a) + .5 * Math.cos(a);
        return net.minecraft.world.phys.Vec3.atBottomCenterOf(main).add(DoubleSwingMotion.rotate(
                new net.minecraft.world.phys.Vec3(0, y / 16, (z - 8) / 16), facing));
    }
    public static double angle(double seconds) {
        return 7.0 * Math.sin(seconds * Math.PI * 2.0 / 2.4);
    }
}
