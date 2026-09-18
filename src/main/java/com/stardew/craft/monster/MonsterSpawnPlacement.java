package com.stardew.craft.monster;

import net.minecraft.world.phys.Vec3;

/** Keeps newly-created physical monsters out of solid terrain before insertion. */
public final class MonsterSpawnPlacement {
    private MonsterSpawnPlacement() {}

    public static boolean ensureClear(StardewMonsterEntity mob) {
        if (mob.isRemoved()) return false;
        if (mob.noPhysics) return true;

        Vec3 origin = mob.position();
        // Exact tangency with a floor or wall is valid; noCollision rejects actual overlap.
        var body = mob.getBoundingBox();
        if (isClear(mob, body)) return true;

        Vec3 best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int x = -4; x <= 4; x++) {
            for (int z = -4; z <= 4; z++) {
                for (int y = -2; y <= 4; y++) {
                    Vec3 offset = new Vec3(x, y, z);
                    double distance = offset.lengthSqr();
                    if (distance >= bestDistance) continue;
                    var candidate = body.move(offset);
                    if (!isClear(mob, candidate)) continue;
                    if (!mob.isNoGravity() && mob.level().noCollision(mob, candidate.move(0, -0.08, 0))) continue;
                    best = origin.add(offset);
                    bestDistance = distance;
                }
            }
        }

        if (best != null) {
            mob.setPos(best);
            return true;
        }
        MonsterFactory.cleanup(mob);
        return false;
    }

    private static boolean isClear(StardewMonsterEntity mob, net.minecraft.world.phys.AABB body) {
        return MonsterSpace.loaded(mob, body)
                && mob.level().noCollision(mob, body)
                && !mob.level().containsAnyLiquid(body);
    }
}
