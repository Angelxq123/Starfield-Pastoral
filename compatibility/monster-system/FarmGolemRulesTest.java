package com.stardew.craft.monster;

import net.minecraft.world.level.levelgen.LegacyRandomSource;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class FarmGolemRulesTest {
    static class Rolls extends LegacyRandomSource {
        private final double[] values; private int i;
        Rolls(double... values){super(0);this.values=values;}
        @Override public double nextDouble(){return i<values.length?values[i++]:.99;}
        @Override public int nextInt(int bound){return bound-1;}
    }
    @Test void iridiumIsRestrictedToCombatNineAndWilderness() {
        assertFalse(FarmGolemRules.iridium(8,true,new Rolls(0)));
        assertFalse(FarmGolemRules.iridium(10,false,new Rolls(0)));
        assertTrue(FarmGolemRules.iridium(9,true,new Rolls(.4999)));
        assertFalse(FarmGolemRules.iridium(9,true,new Rolls(.5)));
    }
    @Test void constructorRollsRemainIndependentAndLevelGated() {
        assertEquals(List.of(),FarmGolemRules.constructorDrops(4,false,new Rolls(0)));
        assertEquals(List.of("omni_geode","mixed_seeds"),FarmGolemRules.constructorDrops(5,false,new Rolls(0,0)));
        assertEquals(List.of("omni_geode","mixed_seeds","iridium_ore","iridium_ore","prismatic_shard","iridium_bar","iridium_bar"),
                FarmGolemRules.constructorDrops(10,true,new Rolls(0,0,0,0,0,0,0)));
    }
    @Test void wildernessHatReplacesRiceAndRiceIsSpringOnly() {
        assertEquals(List.of(new FarmGolemRules.Drop("(H)40",1)),FarmGolemRules.extraDrops(false,0,1,0,new Rolls(.0001,0)));
        var rice=FarmGolemRules.extraDrops(false,0,1,0,new Rolls(.5,.08));
        assertEquals(5,rice.size());assertTrue(rice.stream().allMatch(x->x.id().equals("273")&&x.count()==1));
        assertTrue(FarmGolemRules.extraDrops(false,1,1,0,new Rolls(.5,0)).isEmpty());
    }
    @Test void iridiumSeedsSwitchAtSourceSeasonBoundariesAndQuantityIsOne() {
        String[] seeds={"CarrotSeeds","SummerSquashSeeds","BroccoliSeeds","PowdermelonSeeds"};
        for(int season=0;season<4;season++)for(int offset=0;offset<2;offset++) {
            var drops=FarmGolemRules.extraDrops(true,season,(season==0?23:20)+offset,0,new Rolls(.1,.9,.9,.9,.9,.9,.9));
            assertEquals(List.of(new FarmGolemRules.Drop(seeds[(season+offset)%4],1)),drops);
        }
    }
    @Test void iridiumRepeatedOreAndOtherRewardsAreSeparate() {
        var drops=FarmGolemRules.extraDrops(true,0,1,0,new Rolls(.9,.1,.1,.9,.001,.0001,.0001));
        assertEquals(List.of(new FarmGolemRules.Drop("386",1),new FarmGolemRules.Drop("386",1),
                new FarmGolemRules.Drop("SkillBook_4",1),new FarmGolemRules.Drop("527",1),new FarmGolemRules.Drop("(H)40",1)),drops);
    }
}
