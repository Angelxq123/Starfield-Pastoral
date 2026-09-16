package com.stardew.craft.monster;

import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MonsterSpaceTest {
    private static final AABB BODY=new AABB(-.2,0,-.2,.2,.4,.2);
    @Test void fastShotCannotSkipThinWallOrHitPlayerBehindIt() {
        Vec3 step=new Vec3(12,0,0);
        var wall=MonsterSpace.sweep(BODY,step,new AABB(2,-1,-1,2.0625,2,1));
        var player=MonsterSpace.sweep(BODY,step,new AABB(3,0,-.3,3.6,1.8,.3));
        assertNotNull(wall);assertNotNull(player);assertEquals(1.8/12,wall.fraction(),1e-12);
        assertTrue(wall.fraction()<player.fraction());assertEquals(new Vec3(-1,0,0),wall.normal());
    }
    @Test void overheadFloorStopsVerticalShotAndReflectionRetainsHorizontalSpeed() {
        Vec3 step=new Vec3(.3,4,.2);
        var hit=MonsterSpace.sweep(BODY,step,new AABB(-5,2,-5,5,2.1,5));
        assertNotNull(hit);assertEquals(.4,hit.fraction(),1e-12);
        Vec3 reflected=MonsterSpace.reflect(step,hit.normal());
        assertEquals(new Vec3(.3,-4,.2),reflected);assertEquals(step.length(),reflected.length(),1e-12);
        assertNull(MonsterSpace.sweep(BODY,new Vec3(4,0,0),new AABB(1,2,-1,2,3,1)));
    }
    @Test void tangencyAllowsSlidingAndLeavingButNotEntering() {
        var wall=new AABB(.2,-1,-1,.4,2,1);
        assertNull(MonsterSpace.sweep(BODY,new Vec3(-1,0,0),wall));
        assertNull(MonsterSpace.sweep(BODY,new Vec3(0,0,1),wall));
        assertEquals(0,MonsterSpace.sweep(BODY,new Vec3(1,0,0),wall).fraction());
        assertNull(MonsterSpace.sweep(BODY,Vec3.ZERO,wall));
    }
    @Test void sweepUsesFullBodyNotOnlyCenterRayAndWorksAtNegativeCoordinates() {
        var edge=new AABB(1,0,.15,2,2,.3);
        assertNotNull(MonsterSpace.sweep(BODY,new Vec3(3,0,0),edge));
        var translated=MonsterSpace.sweep(BODY.move(-50,-12,-40),new Vec3(3,0,0),edge.move(-50,-12,-40));
        assertEquals(.8/3,translated.fraction(),1e-12);
    }
    @Test void threeDimensionalAimKeepsSameTotalSpeedAtEveryElevation() {
        for(int y=-20;y<=20;y++){
            Vec3 shot=MonsterSpace.aim(Vec3.ZERO,new AABB(4,y,2,5,y+2,3),.375);
            assertEquals(.375,shot.length(),1e-12);
            assertEquals(Math.signum(y+1),Math.signum(shot.y));
        }
    }
    @Test void oppositeAndVerticalFlightTurnsStayFiniteAndBounded() {
        Vec3 direction=new Vec3(0,0,-1);
        for(int i=0;i<300;i++){
            Vec3 target=i<130?new Vec3(0,0,1):new Vec3(0,1,0);
            Vec3 next=SerpentFlightMotion.turnTowards(direction,target,Math.PI/64);
            assertEquals(1,next.length(),1e-10);
            assertTrue(Math.acos(Math.clamp(direction.dot(next),-1,1))<=Math.PI/64+1e-9);
            direction=next;
        }
        assertTrue(direction.y>.999);
    }
    @Test void chaseClimbsWithoutDiagonalSpeedBonusAndIdleBrakes() {
        var motion=new SerpentFlightMotion();motion.initialize(RandomSource.create(12));
        for(int i=0;i<600;i++){
            motion.elapsed(16);motion.steer(new Vec3(5,5,5),true,true);
            assertTrue(motion.velocity().length()<=7.000001);
        }
        assertTrue(motion.velocity().y>2);
        for(int i=0;i<120;i++)motion.steer(null,false,true);
        assertTrue(motion.velocity().length()<.0001);
    }
    @Test void hitLockSaveAndCollisionKeepStateAcrossAllAxes() {
        var motion=new SerpentFlightMotion();motion.initialize(RandomSource.create(8));
        for(int i=0;i<100;i++)motion.steer(new Vec3(2,5,3),true,true);
        motion.hit();Vec3 locked=motion.heading();
        for(int i=0;i<29;i++){motion.elapsed(i%3==2?18:16);motion.steer(new Vec3(-3,-4,-6),true,true);assertEquals(locked,motion.heading());}
        var copy=new SerpentFlightMotion();copy.load(motion.save());assertEquals(motion.save(),copy.save());
        copy.elapsed(18);copy.steer(new Vec3(-3,-4,-6),true,true);assertNotEquals(locked,copy.heading());
        copy.knockback(9,4);copy.blocked(new Vec3(-1,0,0));assertEquals(0,copy.velocity().x,1e-12);
    }
}
