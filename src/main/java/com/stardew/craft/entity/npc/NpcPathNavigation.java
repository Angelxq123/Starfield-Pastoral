package com.stardew.craft.entity.npc;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/**
 * Custom GroundPathNavigation that uses {@link NpcNodeEvaluator} instead of
 * the vanilla WalkNodeEvaluator. This makes NPCs prefer roads and paved
 * surfaces when planning paths.
 */
public class NpcPathNavigation extends GroundPathNavigation {
    private int initialNodeBudget;
    private BlockPos watchedNode;
    private double bestNodeDistance;
    private long lastNodeProgressTick;
    private boolean recoveryRequested;


    public NpcPathNavigation(Mob mob, Level level) {
        super(mob, level);
    }

    @Override
    protected PathFinder createPathFinder(int maxVisitedNodes) {
        this.nodeEvaluator = new NpcNodeEvaluator();
        this.nodeEvaluator.setCanPassDoors(true);
        this.nodeEvaluator.setCanOpenDoors(true);
        this.nodeEvaluator.setCanFloat(false);
        initialNodeBudget=com.stardew.craft.npc.runtime.NpcNavigationPolicy.current().maxVisitedNodes();
        return new PathFinder(this.nodeEvaluator, initialNodeBudget);
    }
    public void refreshSearchBudget() {
        setMaxVisitedNodesMultiplier((float)com.stardew.craft.npc.runtime.NpcNavigationPolicy.current().maxVisitedNodes()/initialNodeBudget);
    }

    /** Keep automatic path recomputation bounded too; stop/ordinary movement releases the restriction. */
    public boolean moveWithin(Vec3 target,double speed,com.stardew.craft.npc.runtime.NpcSquareArea area) {
        var evaluator=(NpcNodeEvaluator)nodeEvaluator;stop();evaluator.squareArea=area;
        boolean started=false;
        try {
            var candidate=createPath(BlockPos.containing(target),0);
            if(candidate==null||!candidate.canReach())return false;
            for(int i=0;i<candidate.getNodeCount();i++)if(!area.contains(candidate.getEntityPosAtNode(mob,i),mob.getBbWidth()/2.))return false;
            started=moveTo(candidate,speed);return started;
        } finally {if(!started)evaluator.squareArea=null;}
    }

    @Override
    public boolean moveTo(double x,double y,double z,double speed) {
        ((NpcNodeEvaluator)nodeEvaluator).squareArea=null;
        return super.moveTo(x,y,z,speed);
    }

    @Override
    protected void followThePath() {
        Vec3 position = getTempMobPos();
        Vec3 waypoint = path.getNextEntityPos(mob);
        maxDistanceToWaypoint = mob.getBbWidth() > .75F ? mob.getBbWidth() / 2 : .75F - mob.getBbWidth() / 2;
        boolean near = Math.abs(mob.getX() - waypoint.x) <= maxDistanceToWaypoint
                && Math.abs(mob.getZ() - waypoint.z) <= maxDistanceToWaypoint
                && Math.abs(mob.getY() - waypoint.y) < 1;
        if (near) {
            int next = path.getNextNodeIndex() + 1;
            boolean clearTurn = true;
            if (next < path.getNodeCount()) {
                Vec3 following = path.getEntityPosAtNode(mob, next);
                // A waypoint radius is not permission to cut across a wall corner.
                // For level turns check the whole body, not just the centre ray.
                if (Math.abs(getGroundY(following) - mob.getY()) < .05) {
                    Vec3 delta = following.subtract(mob.position());
                    clearTurn = level.noCollision(mob, mob.getBoundingBox()
                            .expandTowards(delta.x, 0, delta.z).deflate(1.0E-7));
                }
            }
            if (clearTurn || mob.position().subtract(waypoint).horizontalDistanceSqr() < .01) path.advance();
        }
        doStuckDetection(position);
    }

    @Override
    protected void doStuckDetection(Vec3 position) {
        BlockPos activeNode = !isDone() ? path.getNextNodePos() : null;
        if (!isDone()) {
            BlockPos next = path.getNextNodePos();
            double distance = mob.position().distanceTo(path.getNextEntityPos(mob));
            long now = level.getGameTime();
            if (!next.equals(watchedNode) || distance < bestNodeDistance - .1) {
                watchedNode = next;
                bestNodeDistance = distance;
                lastNodeProgressTick = now;
            } else if (now - lastNodeProgressTick >= 60) {
                recoveryRequested = true;
            }
        }
        super.doStuckDetection(position);
        if (activeNode != null && isDone()) {
            // Vanilla's timeout calls stop(). Preserve the failure for the executor
            // before its normal idle-path branch can erase it with a fresh moveTo.
            watchedNode = activeNode;
            recoveryRequested = true;
        }
    }

    public boolean needsRecovery() {
        return recoveryRequested;
    }

    /** Remember the failed approach briefly, then force a fresh search instead of cached createPath. */
    public void prepareRecovery() {
        BlockPos failed = !isDone() ? path.getNextNodePos() : watchedNode;
        if (failed != null) ((NpcNodeEvaluator) nodeEvaluator).penalize(failed, level.getGameTime());
        stop();
    }

    @Override
    public void stop() {
        super.stop();
        ((NpcNodeEvaluator)nodeEvaluator).squareArea=null;
        watchedNode = null;
        recoveryRequested = false;
    }
}
