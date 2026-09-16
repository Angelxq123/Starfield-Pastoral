package com.stardew.craft.monster;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

/** Vanilla XYZ path search supplies waypoints; species steering remains responsible for movement. */
public final class MonsterFlightRoute extends FlyingPathNavigation {
    private Path route;
    private Vec3 destination, escape;
    private long nextSearch;
    public MonsterFlightRoute(Mob mob, Level level) {
        super(mob, level);
        setCanOpenDoors(false);
        setCanPassDoors(false);
        setCanFloat(false);
        setMaxVisitedNodesMultiplier(2);
    }
    public boolean clear(Vec3 feet) {
        Vec3 delta = feet.subtract(mob.position());
        return delta.lengthSqr() <= 32 * 32
                && MonsterSpace.blocks(mob, mob.getBoundingBox().inflate(.025), delta) == null;
    }
    public void invalidate() { route = null; escape = null; }
    public void blocked(Vec3 normal) {
        route = null;
        escape = mob.position().add(normal.scale(.6));
    }
    public Vec3 move(MonsterFlightMotion motion) {
        Vec3 delta=motion.velocity().scale(1./64),before=mob.position();
        var wall=MonsterSpace.blocks(mob,mob.getBoundingBox().inflate(.025),delta);
        mob.move(net.minecraft.world.entity.MoverType.SELF,delta.scale(wall==null?1:Math.max(0,wall.fraction()-1e-5)));
        if(wall!=null){motion.blocked(wall.normal());blocked(wall.normal());}
        return mob.position().subtract(before);
    }
    public Vec3 waypoint(Vec3 feet) {
        if (feet == null) { route = null; destination = null; escape = null; return null; }
        if (escape != null) {
            Vec3 delta = escape.subtract(mob.position());
            if (delta.lengthSqr() > .08 && MonsterSpace.blocks(mob, mob.getBoundingBox(), delta) == null) return escape;
            escape = null;
        }
        if (clear(feet)) { route = null; return feet; }
        long now = level.getGameTime();
        boolean moved = destination == null || destination.distanceToSqr(feet) > 2.25;
        if (now >= nextSearch && (route == null || route.isDone() || moved)) {
            nextSearch = now + 20;
            destination = feet;
            // Bound search to a local volume; PathNavigationRegion uses getChunkNow (no loading).
            route = createPath(java.util.Set.of(BlockPos.containing(feet)), 4, false, 0, 64);
        }
        if (route == null || route.isDone()) return null;
        // Skip already reached points, then use the furthest nearby node with full body clearance.
        while (!route.isDone() && mob.position().distanceToSqr(route.getNextEntityPos(mob)) < .16) route.advance();
        if (route.isDone()) return null;
        for (int i = Math.min(route.getNodeCount() - 1, route.getNextNodeIndex() + 4); i >= route.getNextNodeIndex(); i--) {
            Vec3 node = route.getEntityPosAtNode(mob, i).add(0, .05, 0);
            if (clear(node)) { route.setNextNodeIndex(i); return node; }
        }
        route = null;
        return null;
    }
}
