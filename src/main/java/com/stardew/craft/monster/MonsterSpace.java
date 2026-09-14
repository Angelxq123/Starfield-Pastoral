package com.stardew.craft.monster;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Actual world-space body sweeps, shared by flight and projectiles. Units are MC blocks. */
public final class MonsterSpace {
    private MonsterSpace() {}
    public record Hit(double fraction, Vec3 normal) {}
    public static Vec3 aim(Vec3 origin, AABB target, double speed) {
        return target.getCenter().subtract(origin).normalize().scale(speed);
    }
    public static Vec3 reflect(Vec3 velocity, Vec3 normal) {
        return velocity.subtract(normal.scale(2 * velocity.dot(normal)));
    }
    public static boolean loaded(Entity entity, AABB box) {
        return box.minY >= entity.level().getMinBuildHeight() && box.maxY <= entity.level().getMaxBuildHeight()
                && entity.level().getWorldBorder().isWithinBounds(box)
                && entity.level().hasChunksAt(BlockPos.containing(box.minX, box.minY, box.minZ),
                    BlockPos.containing(box.maxX, box.maxY, box.maxZ));
    }
    public static Hit blocks(Entity entity, AABB body, Vec3 delta) {
        if (!loaded(entity, body.expandTowards(delta))) return new Hit(0, delta.normalize().scale(-1));
        // Long diagonal visibility routes must not scan the entire bounding cuboid.
        // Each small segment still sweeps the complete body, with a global hit fraction.
        int segments = Math.max(1, (int) Math.ceil(delta.length() / 4));
        Vec3 step = delta.scale(1.0 / segments);
        for (int i = 0; i < segments; i++) {
            AABB part = body.move(step.scale(i));
            Hit closest = null;
            for (var shape : entity.level().getBlockCollisions(entity, part.expandTowards(step).inflate(1e-6))) {
                for (var box : shape.toAabbs()) {
                    Hit hit = sweep(part, step, box);
                    if (hit != null && (closest == null || hit.fraction < closest.fraction)) closest = hit;
                }
            }
            if (closest != null) return new Hit((i + closest.fraction) / segments, closest.normal);
        }
        return null;
    }

    /** Slab intersection of the moving box against a static box, including its full thickness. */
    public static Hit sweep(AABB body, Vec3 delta, AABB obstacle) {
        double[] lo = {body.minX, body.minY, body.minZ}, hi = {body.maxX, body.maxY, body.maxZ};
        double[] wallLo = {obstacle.minX, obstacle.minY, obstacle.minZ}, wallHi = {obstacle.maxX, obstacle.maxY, obstacle.maxZ};
        double[] movement = {delta.x, delta.y, delta.z};
        double enter = -Double.MAX_VALUE, leave = Double.MAX_VALUE;
        Vec3 normal = Vec3.ZERO;
        for (int axis = 0; axis < 3; axis++) {
            double v = movement[axis];
            if (Math.abs(v) < 1e-12) {
                // Mere tangency is not penetration: allow sliding along a floor/wall.
                if (hi[axis] <= wallLo[axis] || lo[axis] >= wallHi[axis]) return null;
                continue;
            }
            double a = (wallLo[axis] - hi[axis]) / v, b = (wallHi[axis] - lo[axis]) / v;
            double near = Math.min(a, b), far = Math.max(a, b);
            if (near > enter) {
                enter = near;
                double sign = -Math.signum(v);
                normal = new Vec3(axis == 0 ? sign : 0, axis == 1 ? sign : 0, axis == 2 ? sign : 0);
            }
            leave = Math.min(leave, far);
            if (enter > leave) return null;
        }
        if (leave <= 0 || enter > 1) return null;
        if (enter < 0 && !body.intersects(obstacle)) return null;
        return new Hit(Math.max(0, enter), normal);
    }
}
