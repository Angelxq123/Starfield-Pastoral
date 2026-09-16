package com.stardew.craft.monster;

import com.stardew.craft.mining.OrdinaryMineRuntime;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import java.util.Comparator;
import java.util.function.Predicate;

/** Ordered continuous collision: a player behind a wall cannot be hit in the same substep. */
public final class MonsterProjectileMovement {
    private MonsterProjectileMovement() {}
    private record PlayerHit(ServerPlayer player, double fraction) {}
    public static MonsterSpace.Hit step(Projectile shot, Vec3 delta, boolean playersEnabled,
                                       boolean owned, int floor, Predicate<ServerPlayer> hitPlayer) {
        var origin = shot.position();
        var body = shot.getBoundingBox();
        var wall = MonsterSpace.blocks(shot, body, delta);
        double limit = wall == null ? 1.000001 : wall.fraction();
        if (playersEnabled) {
            var hits = new java.util.ArrayList<PlayerHit>();
            for (var player : ((ServerLevel) shot.level()).players()) {
                if (!player.isAlive() || player.isCreative() || player.isSpectator()
                        || (owned && OrdinaryMineRuntime.floorAt(player.blockPosition()) != floor)) continue;
                var hit = MonsterSpace.sweep(body, delta, player.getBoundingBox());
                if (hit != null && hit.fraction() < limit) hits.add(new PlayerHit(player, hit.fraction()));
            }
            hits.sort(Comparator.comparingDouble(PlayerHit::fraction));
            for (var hit : hits) {
                shot.setPos(origin.add(delta.scale(hit.fraction())));
                if (hitPlayer.test(hit.player()) || shot.isRemoved()) return null;
            }
        }
        // Leave a tiny gap to avoid starting the reflected substep inside the wall.
        shot.setPos(origin.add(delta.scale(wall == null ? 1 : Math.max(0, wall.fraction() - 1e-5))));
        return wall;
    }
}
