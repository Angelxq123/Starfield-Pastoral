package com.stardew.craft.monster;

import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class InsectLifecycleTest {
    @Test void healthyGrubDoesNotBeginTimerAndThresholdUsesOriginalMaxHealth(){
        var g=new GrubLifecycle();for(int i=0;i<1000;i++)g.step(16,9,20,false,2);
        assertEquals(2000,g.remaining());assertEquals(GrubLifecycle.MOBILE,g.phase());
        g.step(16,8,20,false,2);assertEquals(1984,g.remaining());assertEquals(GrubLifecycle.RETREAT,g.phase());
        g.step(100,9,20,false,2);assertEquals(1984,g.remaining());
    }
    @Test void formationCarriesWalkingFrameAndPupaIs4500Milliseconds(){
        var g=new GrubLifecycle();for(int i=0;i<3;i++)g.step(196,20,20,true,2);
        assertEquals(3,g.frame());g.step(2000,8,20,false,2);
        // Carried timer is reset after a frame advance; a large elapsed forms frame 16 after wrapping 19.
        assertEquals(GrubLifecycle.FORM,g.phase());
        for(int i=0;i<3;i++)g.step(126,8,20,false,2);
        assertEquals(GrubLifecycle.PUPA,g.phase());assertEquals(4500,g.remaining());
        g.step(4499,20,20,false,2);assertFalse(g.ready());g.step(1,20,20,false,2);assertTrue(g.ready());
    }
    @Test void savingRetreatOrPupaDoesNotRestartCountdown(){
        for(int updates:new int[]{40,145,210}){
            var a=new GrubLifecycle();for(int i=0;i<updates;i++)a.step(16,8,20,true,1);
            var b=new GrubLifecycle();b.load(a.save());
            for(int i=0;i<400;i++){a.step(16,8,20,true,1);b.step(16,8,20,true,1);assertEquals(a.save(),b.save());}
        }
    }
    @Test void emergencePrecedesSteeringAndHitFreezesRotationOnly(){
        var f=new FlySourceSteering();var r=RandomSource.create(42);
        for(int i=0;i<63;i++)f.animate(16,300,100,true,r);
        assertEquals(0,f.xVelocity);assertEquals(0,f.yVelocity);assertTrue(f.spawnRemaining<0);
        f.animate(16,300,100,true,r);assertNotEquals(0,f.rotation);
        f.hitRemaining=500;double rotation=f.rotation,velocity=f.yVelocity;
        for(int i=0;i<31;i++)f.animate(16,-200,-300,true,r);
        assertEquals(rotation,f.rotation);assertNotEquals(velocity,f.yVelocity);
        f.animate(16,-200,-300,true,r);assertNotEquals(rotation,f.rotation);
    }
    @Test void modelForwardMatchesSourceFlightDirection(){
        var f=new FlySourceSteering();
        for(double angle:new double[]{0,Math.PI/2,Math.PI,-Math.PI/2}){
            f.rotation=angle;double yaw=Math.toRadians(f.minecraftYaw());
            assertEquals(Math.sin(angle),-Math.sin(yaw),1e-6);
            assertEquals(-Math.cos(angle),Math.cos(yaw),1e-6);
        }
    }
    @Test void savedFlyPreservesInertiaAndNextRandomDrivenTrajectory(){
        var a=new FlySourceSteering();a.spawnRemaining=-1;a.slipperiness=14;a.hitRemaining=500;
        var r=RandomSource.create(7);for(int i=0;i<20;i++){a.decay();a.animate(16,320,-100,true,r);}
        var b=new FlySourceSteering();b.load(a.save());var ra=RandomSource.create(22);var rb=RandomSource.create(22);
        for(int i=0;i<200;i++){a.decay();b.decay();a.animate(16,200,200,true,ra);b.animate(16,200,200,true,rb);assertEquals(a.save(),b.save());}
    }
}
