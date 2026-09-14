package com.stardew.craft.monster;
import com.stardew.craft.client.light.ColoredLightEngine;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
class GhostLightingTest {
    @Test void movingEmissionPreservesSunlightAndBrighterLamps()throws Exception{
        var field=ColoredLightEngine.class.getDeclaredField("samples");field.setAccessible(true);Object old=field.get(null);
        try{
            field.set(null,Map.of(new BlockPos(0,0,0).asLong(),160<<24|0xc4e5d3));
            assertEquals(15<<20|160,ColoredLightEngine.light(.5,.5,.5,15<<20));
            assertEquals(15<<20|240,ColoredLightEngine.light(.5,.5,.5,15<<20|240));
            assertEquals(0,ColoredLightEngine.light(8,8,8,0));
            field.set(null,Map.of(new BlockPos(0,0,0).asLong(),0xc4e5d3));
            assertEquals(32,ColoredLightEngine.light(.5,.5,.5,32),"Static tint-only fields must not brighten lamps");
        }finally{field.set(null,old);}
    }
    @Test void fieldUnionUsesMaximumEmissionWithoutOverflowingColor(){
        assertEquals(160,ColoredLightEngine.combine(160<<24|0xff0000,80<<24|0x00ff80)>>>24);
        assertEquals(0xffff80,ColoredLightEngine.combine(160<<24|0xff0000,80<<24|0x00ff80)&0xffffff);
    }
}
