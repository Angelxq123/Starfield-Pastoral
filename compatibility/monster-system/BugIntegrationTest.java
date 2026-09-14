package com.stardew.craft.monster;

import com.google.gson.*;
import com.stardew.craft.client.monsternative.NativeBugPlayback;
import com.stardew.craft.client.npcnative.NativeNpcModel;
import com.stardew.craft.entity.ModEntities;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;
import java.io.InputStreamReader;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class BugIntegrationTest {
    @Test void ordinaryBugUsesSourceDefinitionAndBornLoot() throws Exception {
        assertEquals("entity.stardewcraft.bug",ModEntities.BUG.get().getDescriptionId());
        try(var r=new InputStreamReader(getClass().getResourceAsStream("/data/stardewcraft/monsters/bug.json"))) {
            var d=MonsterDefinition.parse(ResourceLocation.parse("stardewcraft:bug"),JsonParser.parseReader(r).getAsJsonObject());
            assertEquals("Bug",d.sourceName());assertEquals(1,d.health());assertEquals(8,d.damage());
            assertEquals(0,d.resilience());assertEquals(0,d.missChance());assertEquals(1,d.experience());
            assertEquals(List.of(.76,.02,.005,.005,.001),d.drops().stream().map(MonsterDefinition.Drop::chance).toList());
            assertEquals(List.of("bug_meat","white_algae","ancient_seed","dwarf_scroll_i","dwarf_scroll_iv"),d.drops().stream().map(x->ResourceLocation.parse(x.item()).getPath()).toList());
            for(var drop:d.drops())assertTrue(net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse(drop.item())));
            for(int seed=0;seed<100;seed++) {
                var context=new MonsterSpawnContext(MonsterSpawnContext.Source.COMMAND,20,true,null);
                var stats=MonsterStatResolver.base(d,context,RandomSource.create(seed));
                assertEquals(1,stats.initialHealth());assertTrue(stats.combat().getDamage()>=8&&stats.combat().getDamage()<=11);
                var state=new MonsterState(d,context,RandomSource.create(seed));assertEquals(state.save(),MonsterState.load(state.save()).save());
            }
        }
    }
    @Test void shippedPlaybackKeepsWorldSwayContinuousAcrossReversalAndFreezesAtDeath() throws Exception {
        try(var r=new InputStreamReader(getClass().getResourceAsStream("/assets/stardewcraft/monster_native/bug.json"))) {
            var m=new Gson().fromJson(r,NativeNpcModel.class);assertEquals(4,m.clips().size());
            assertEquals(4,m.quads().stream().filter(NativeNpcModel.Quad::translucent).count());
            var p=new NativeBugPlayback(m);p.sample(.24,2,0,0);double x=p.swayX();
            p.sample(.26,2,0,2);assertEquals(x,p.swayX(),1e-8);assertEquals(0,p.swayZ());
            p.sample(.27,2,.01,2);double deathX=p.swayX();
            var matrices=java.util.Arrays.stream(p.pose().matrices()).map(v->v.get(new float[16])).toList();
            p.sample(.27,2,.01,2);
            for(int i=0;i<matrices.size();i++)assertArrayEquals(matrices.get(i),p.pose().matrices()[i].get(new float[16]));
            p.sample(.6,2,.34,2);assertEquals(deathX,p.swayX(),1e-8);
            var late=new NativeBugPlayback(m);late.sample(.6,2,.34,2);assertEquals(deathX,late.swayX(),1e-8);
            p.sample(.25,2,0,1);assertEquals(0,p.swayX());assertEquals(10./64,p.swayZ(),1e-8);
        }
    }
}
