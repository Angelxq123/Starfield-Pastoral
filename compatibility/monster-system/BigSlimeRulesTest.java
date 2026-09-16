package com.stardew.craft.monster;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class BigSlimeRulesTest {
 @Test void sourceAreaBoundariesAndConstructorMultipliers(){int[] floors={1,39,40,79,80,120,121,500};int[] area={0,0,40,40,80,80,121,121};for(int i=0;i<floors.length;i++)assertEquals(area[i],BigSlimeRules.area(floors[i]));assertEquals(4,BigSlimeRules.healthMultiplier(121));assertEquals(3,BigSlimeRules.damageMultiplier(121));assertEquals(3,BigSlimeRules.experienceMultiplier(121));assertEquals(1,BigSlimeRules.damageMultiplier(40));}
 @Test void splitRollIsSeventyFivePercentAndTwoToFourNotBreeding(){var r=RandomSource.create(91991);int split=0;int[] counts=new int[5];for(int i=0;i<100000;i++){int n=BigSlimeRules.splitCount(r);assertTrue(n==0||n>=2&&n<=4);if(n>0){split++;counts[n]++;}}assertTrue(split>74400&&split<75600);for(int i=2;i<=4;i++)assertTrue(counts[i]>24200&&counts[i]<25800);}
 @Test void brightnessPreservesSourceAlphaAndCakeConsumesOneRoll(){for(int area:new int[]{0,40,80,121})for(int seed=0;seed<80;seed++){var r=RandomSource.create(seed);int c=BigSlimeRules.color(area,r);assertTrue(java.util.Set.of(178,204,229,255).contains(c>>>24));var expected=RandomSource.create(seed);var actual=RandomSource.create(seed);boolean cake=expected.nextDouble()<.01&&area>=40;assertEquals(cake,BigSlimeRules.holdsCake(area,actual));assertEquals(expected.nextLong(),actual.nextLong());}}
}
