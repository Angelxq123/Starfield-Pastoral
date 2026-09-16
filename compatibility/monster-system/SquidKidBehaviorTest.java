package com.stardew.craft.monster;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class SquidKidBehaviorTest {
    private LegacyRandomSource zero(){return new LegacyRandomSource(0){@Override public double nextDouble(){return 0;}@Override public int nextInt(int bound){return 0;}};}
    @Test void earliestCooldownIsSeventyFiveSourceStepsAndNeverFiresOutsideNotice(){
        var c=new SquidKidBehavior();var r=zero();assertEquals(SquidKidBehavior.Action.NONE,c.step(false,10,0,r));assertEquals(SquidKidBehavior.Action.FIRE,c.step(true,5,0,r));assertEquals(1200,c.cooldown());
        for(int i=0;i<74;i++)assertEquals(SquidKidBehavior.Action.NUDGE,c.step(true,5,0,r));assertEquals(16,c.cooldown());assertEquals(SquidKidBehavior.Action.FIRE,c.step(true,5,0,r));
        var copy=new SquidKidBehavior();copy.load(c.save());assertEquals(c.save(),copy.save());
    }
    @Test void trajectoryUsesWholeSourcePixelsAndEightSlipperinessAfterNudge(){
        var c=new SquidKidBehavior();var r=zero();c.step(true,3,4,r);c.step(true,3,4,r);assertEquals(4,c.x());assertEquals(6,c.z());c.reflect(true,false);c.decay(true);assertEquals(-3.875,c.x());assertEquals(5.8125,c.z());
        c.knockback(1,20);assertEquals(-3.875,c.x());assertEquals(20,c.z());
    }
    @Test void bobUsesHalfSineMillisecondComponentNotAnInventedFullSine(){assertEquals(43./64,SquidKidBehavior.lift(0,0));assertEquals(28./64,SquidKidBehavior.lift(10,0));assertEquals(SquidKidBehavior.lift(0,0),SquidKidBehavior.lift(20,0));for(int i=0;i<60;i++)assertTrue(SquidKidBehavior.lift(i/3,i%3)>=28./64);}
}
