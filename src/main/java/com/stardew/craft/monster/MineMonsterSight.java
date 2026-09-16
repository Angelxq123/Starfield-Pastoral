package com.stardew.craft.monster;

import com.stardew.craft.mining.OrdinaryMineRuntime;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;

/** Visibility follows the current 3D world, including floors and player-placed obstacles. */
public final class MineMonsterSight {
    private MineMonsterSight() {}
    public static boolean sees(StardewMonsterEntity mob, Player player, int range) {
        if (mob.monsterState().context().generation() != null
                && OrdinaryMineRuntime.floorAt(player.blockPosition()) != mob.monsterState().context().floor()) return false;
        Vec3 origin = mob.getBoundingBox().getCenter(), target = player.getBoundingBox().getCenter();
        if (origin.distanceToSqr(target) > (double) range * range
                || !MonsterSpace.loaded(mob, new AABB(origin, target))) return false;
        return mob.level().clip(new ClipContext(origin, target, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, mob)).getType() == HitResult.Type.MISS;
    }
}
