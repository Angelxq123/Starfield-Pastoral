package com.stardew.craft.monster;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class SerpentSteeringTest {
 @Test void hitPausesTurningForHalfSecondButDoesNotFreezeMovementOrAcceleration(){var s=new SerpentSourceSteering();var r=RandomSource.create(12);s.initialize(r);s.animate(500,200,r);s.hit();double angle=s.rotation(),vx=s.x();for(int i=0;i<29;i++){s.elapsed(i%3==2?18:16);s.animate(-500,-200,r);assertEquals(angle,s.rotation(),0);}assertNotEquals(vx,s.x());s.elapsed(18);s.animate(-500,-200,r);assertNotEquals(angle,s.rotation());}
 @Test void nativeGliderInertiaUsesPersistedSlipperinessAndSourceMagnitudeWins(){var s=new SerpentSourceSteering();s.initialize(RandomSource.create(42));assertTrue(s.slipperiness()>=24&&s.slipperiness()<=33);s.knockback(5,4);s.knockback(-3,-2);assertEquals(5,s.x());assertEquals(4,s.z());s.decay();assertEquals(5-5./s.slipperiness(),s.x(),1e-10);var copy=new SerpentSourceSteering();copy.load(s.save());assertEquals(s.save(),copy.save());for(int i=0;i<200;i++)copy.decay();assertEquals(0,copy.x());assertEquals(0,copy.z(),0);}
 @Test void longPursuitAndRepeatedKnockbacksRemainFinite(){var s=new SerpentSourceSteering();var r=RandomSource.create(91);s.initialize(r);for(int i=0;i<12000;i++){s.decay();s.elapsed(i%3==2?18:16);if(i%101==0){s.hit();s.knockback(-4,6);}s.animate(Math.sin(i*.03)*640,Math.cos(i*.03)*640,r);assertTrue(Double.isFinite(s.x())&&Double.isFinite(s.z()));assertTrue(Math.abs(s.x())<9&&Math.abs(s.z())<9);}}
}
