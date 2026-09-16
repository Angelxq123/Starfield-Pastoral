package com.stardew.craft.monster;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class MummyLifecycleTest {
 @Test void tenSecondsStartsAfterFrameNineteenAndDamageReturnsAtRevivalStart(){var c=new MummyLifecycle();c.crumble();for(int i=0;i<5;i++){c.tick(50);assertEquals(MummyLifecycle.CRUMBLE,c.phase());assertEquals(10000,c.remaining());}c.tick(50);assertEquals(MummyLifecycle.DOWNED,c.phase());for(int i=0;i<160;i++)c.tick(50);assertEquals(2000,c.remaining());assertFalse(c.shaking());c.tick(50);assertTrue(c.shaking());for(int i=0;i<39;i++)c.tick(50);assertEquals(MummyLifecycle.REVIVE,c.phase());assertFalse(c.collapsed());for(int i=0;i<7;i++){c.tick(50);assertEquals(MummyLifecycle.REVIVE,c.phase());}c.tick(50);assertEquals(MummyLifecycle.WALK,c.phase());}
 @Test void loadingDoesNotRestartCountdown(){var c=new MummyLifecycle();c.load(MummyLifecycle.DOWNED,7500,2500);c.tick(50);assertEquals(2450,c.remaining());assertFalse(c.shaking());c.crumble();assertEquals(10000,c.remaining());}
}
