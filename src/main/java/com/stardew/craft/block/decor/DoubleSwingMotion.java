package com.stardew.craft.block.decor;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/** The model and passenger use exactly the same pivot, clock and winter rule. */
public final class DoubleSwingMotion {
    private DoubleSwingMotion() {}
    public static double seconds(long ticks, float partialTick) { return (Math.floorMod(ticks, 80) + partialTick) / 20.0; }
    public static double angle(int seat, double seconds, boolean frozen) {
        return frozen ? 0 : (seat == 0 ? 12 : 16) * Math.sin(seconds * Math.PI / 2 + (seat == 0 ? 0 : 1.65));
    }
    public static Vec3 rotate(Vec3 v, Direction facing) {
        return switch (facing) {
            case EAST -> new Vec3(-v.z, v.y, v.x);
            case SOUTH -> new Vec3(-v.x, v.y, -v.z);
            case WEST -> new Vec3(v.z, v.y, -v.x);
            default -> v;
        };
    }
    public static Vec3 surface(BlockPos main, Direction facing, int seat, double seconds, boolean frozen) {
        double angle = Math.toRadians(angle(seat, seconds, frozen));
        // Source surface (24/56,16,24), pivot (24/56,72,24); origin shifts by (32,0,16).
        Vec3 local = rotate(new Vec3(seat == 0 ? -1 : 1, (72 - 56 * Math.cos(angle)) / 16,
                -56 * Math.sin(angle) / 16), facing);
        return Vec3.atBottomCenterOf(main).add(local);
    }
}
