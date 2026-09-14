package com.stardew.craft.monster;

/** Bat.behaviorAtGameTick + Monster.MovePosition, in source pixels at 60 Hz. */
public final class BatSourceFlight {
    public static final int STEPS_PER_TICK = 3;
    public static final double WAKE_SECONDS = .56;
    public double x, z, rotation;
    public boolean turningRight;
    public int hitMillis;
    public int slipperiness = 20;
    public double extraVelocity;
    public double maxSpeed = 5;

    public static boolean withinNoticeRange(int tileX, int tileZ, int playerX, int playerZ) {
        return Math.abs(tileX - playerX) <= 6 && Math.abs(tileZ - playerZ) <= 6;
    }

    public void steer(double dx, double dz, boolean turnChoice, int jitterX, int jitterZ) {
        double distance = Math.max(1, Math.abs(dx) + Math.abs(dz));
        if (distance < (extraVelocity > 0 ? 192 : 64)) {
            x = Math.clamp(x * 1.05, -maxSpeed, maxSpeed);
            z = Math.clamp(z * 1.05, -maxSpeed, maxSpeed);
        }
        if (hitMillis <= 0) {
            double target = Math.atan2(-dz, -dx) - Math.PI / 2;
            if (Math.abs(target) - Math.abs(rotation) > Math.PI * 7 / 8 && turnChoice) turningRight = true;
            else if (Math.abs(target) - Math.abs(rotation) < Math.PI / 8) turningRight = false;
            rotation += (turningRight ? -1 : 1) * Math.signum(target - rotation) * Math.PI / 64;
            rotation %= Math.PI * 2;
        }
        double acceleration = Math.min(5, Math.max(1, 5 - distance / 128)) + extraVelocity;
        double headingX = -Math.cos(rotation + Math.PI / 2);
        double headingZ = -Math.sin(rotation + Math.PI / 2);
        x += headingX * acceleration / 6 + jitterX / 100.0;
        // Source Y velocity is inverted when moving on the map; Z here is already world-facing.
        z += headingZ * acceleration / 6 - jitterZ / 100.0;
        if (Math.abs(x) > Math.abs(headingX * maxSpeed)) x -= headingX * acceleration / 6;
        if (Math.abs(z) > Math.abs(headingZ * maxSpeed)) z -= headingZ * acceleration / 6;
    }

    public void decay(boolean blocked) {
        x = decay(x, slipperiness * (blocked ? 4 : 1));
        z = decay(z, slipperiness * (blocked ? 4 : 1));
    }
    private static double decay(double velocity, int divisor) {
        double result = velocity - velocity / divisor;
        return Math.abs(result) <= .05 ? 0 : result;
    }
}
