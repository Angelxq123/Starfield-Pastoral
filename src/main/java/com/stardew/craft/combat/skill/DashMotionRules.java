package com.stardew.craft.combat.skill;

import net.minecraft.world.phys.Vec3;

/** Identical horizontal motion on both sides; travel owns position, gravity and collisions. */
public final class DashMotionRules {
    private DashMotionRules() {}

    public static Vec3 horizontalVelocity(Vec3 current, Vec3 end, double speed) {
        Vec3 remaining = end.subtract(current).multiply(1, 0, 1);
        double distance = remaining.length();
        if (distance < 1.0e-5 || speed <= 0) return Vec3.ZERO;
        return remaining.scale(Math.min(speed, distance) / distance);
    }
}
