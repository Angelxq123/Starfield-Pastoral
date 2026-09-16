package com.stardew.craft.monster;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DuggyLifecycleTest {
    @Test void hiddenPhaseWaitsForAnEligibleTileAndCannotHurt(){
        var d=new DuggyLifecycle();for(int i=0;i<200;i++)assertFalse(d.step(16,false));
        assertTrue(d.hidden());assertEquals(0,d.damage());assertEquals(0,d.cursor());
    }
    @Test void damageStartsAtFourAndContinuesThroughoutRetraction(){
        var d=new DuggyLifecycle();boolean sawRetreat=false,reset=false;
        for(int i=0;i<130;i++){
            boolean cleared=d.step(16,true);
            if(d.frame()>=8){sawRetreat=true;assertEquals(8,d.damage());}
            if(d.frame()<4)assertEquals(0,d.damage());
            if(cleared){assertTrue(d.hidden());assertEquals(0,d.damage());reset=true;break;}
        }
        assertTrue(sawRetreat);assertTrue(reset);
    }
    @Test void stageBoundaryExecutesBothSourceBranchesAndPreservesTheTimer(){
        var d=new DuggyLifecycle();for(int i=0;i<3;i++)d.step(101,true);
        assertEquals(3,d.frame());d.step(101,true);
        assertEquals(5,d.frame()); // AnimateDown crosses to 4; the same call executes AnimateRight.
        var restored=new DuggyLifecycle();restored.load(d.save());
        for(int i=0;i<100;i++){assertEquals(d.step(16,true),restored.step(16,true));assertEquals(d.save(),restored.save());}
    }
}
