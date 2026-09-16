package com.stardew.craft.monster;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ShamanSpellClockTest {
    @Test void warmupWindsUpWithCarriedSixteenMillisecondsAndHitExtension(){
        var c=new ShamanSpellClock();assertEquals(ShamanSpellClock.Action.SPOTTED,c.step(true,true,false,false,true,0));
        for(int i=0;i<94;i++)assertEquals(ShamanSpellClock.Action.NONE,c.step(true,true,false,false,true,0));
        assertEquals(-4,c.cooldown());assertEquals(ShamanSpellClock.Action.START_CAST,c.step(true,true,false,false,true,0));assertEquals(484,c.cooldown());c.delayFromHit();assertEquals(684,c.cooldown());
        var saved=new ShamanSpellClock();saved.load(c.save());assertEquals(c.save(),saved.save());int frames=0;while(c.casting()){c.step(false,false,false,false,false,1);frames++;assertTrue(frames<50);}assertEquals(43,frames);assertEquals(1500,c.cooldown());assertTrue(c.walking());
    }
    @Test void fleePrecedesSightAndPathPrecedesSpellButExistingPathCanBeInterrupted(){
        var c=new ShamanSpellClock();c.step(true,true,false,false,true,0);assertEquals(ShamanSpellClock.Action.FLEE,c.step(true,true,true,false,false,0));assertFalse(c.walking());assertEquals(ShamanSpellClock.Action.FIND_PATH,c.step(true,true,false,false,false,0));for(int i=0;i<100;i++)c.step(true,true,false,true,false,1);assertEquals(ShamanSpellClock.Action.START_CAST,c.step(true,true,false,true,false,0));
    }
}
