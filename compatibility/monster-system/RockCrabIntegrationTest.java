package com.stardew.craft.monster;

import com.google.gson.*;
import com.stardew.craft.client.monsternative.NativeRockCrabPlayback;
import com.stardew.craft.client.npcnative.NativeNpcModel;
import com.stardew.craft.entity.ModEntities;
import com.stardew.craft.entity.monster.RockCrabEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;
import java.io.InputStreamReader;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class RockCrabIntegrationTest {
    @Test void registeredCrabUsesSourceStatsLootAndProgression() throws Exception {
        assertEquals("entity.stardewcraft.rock_crab",ModEntities.ROCK_CRAB.get().getDescriptionId());
        try(var reader=new InputStreamReader(getClass().getResourceAsStream("/data/stardewcraft/monsters/rock_crab.json"))) {
            var d=MonsterDefinition.parse(ResourceLocation.parse("stardewcraft:rock_crab"),JsonParser.parseReader(reader).getAsJsonObject());
            assertEquals("Rock Crab",d.sourceName());assertEquals(30,d.health());assertEquals(5,d.damage());assertEquals(1,d.resilience());assertEquals(4,d.experience());assertEquals(0,d.missChance());
            assertEquals(List.of(.15,.4,.005,.001),d.drops().stream().map(MonsterDefinition.Drop::chance).toList());
            assertEquals(List.of("crab","cherry_bomb","dwarf_scroll_i","dwarf_scroll_iv"),d.drops().stream().map(x->ResourceLocation.parse(x.item()).getPath()).toList());
            for(var drop:d.drops())assertTrue(net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse(drop.item())));
            for(int floor:new int[]{1,15,30}) {
                var context=new MonsterSpawnContext(MonsterSpawnContext.Source.ORDINARY_MINE,floor,false,null);
                var stats=MonsterStatResolver.base(d,context,RandomSource.create(1));assertEquals(30,stats.initialHealth());assertEquals(5,stats.combat().getDamage());
            }
            for(int seed=0;seed<100;seed++) {
                var context=new MonsterSpawnContext(MonsterSpawnContext.Source.COMMAND,30,true,null);
                var stats=MonsterStatResolver.base(d,context,RandomSource.create(seed));
                assertTrue(stats.initialHealth()>=30&&stats.initialHealth()<60);assertTrue(stats.combat().getDamage()>=5&&stats.combat().getDamage()<=6);
                var state=new MonsterState(d,context,RandomSource.create(seed));assertEquals(state.save(),MonsterState.load(state.save()).save());
            }
        }
        assertTrue(RockCrabEntity.withinNotice(0,0,3,3));assertFalse(RockCrabEntity.withinNotice(0,0,4,0));
    }
    @Test void runtimeConcealsNewAndRetractedCrabAndNeverRestoresBrokenShell() throws Exception {
        try(var reader=new InputStreamReader(getClass().getResourceAsStream("/assets/stardewcraft/monster_native/rock_crab.json"))) {
            var model=new Gson().fromJson(reader,NativeNpcModel.class);assertEquals(15,model.clips().size());
            var playback=new NativeRockCrabPlayback(model);
            playback.sample(0,false,0,0,1,0);
            assertTrue(playback.visible("shell_main"));assertFalse(playback.visible("body_outline"));
            for(int i=1;i<=60;i++)playback.sample(1,true,i/60.,i/60.,1,0);
            assertTrue(playback.visible("body_outline"));
            for(int i=1;i<=60;i++)playback.sample(0,false,i/60.,1+i/60.,1,0);
            assertFalse(playback.visible("body_outline"));assertTrue(playback.visible("shell_main"));
            for(int i=1;i<=120;i++) {
                playback.sample(2,true,i/60.,2+i/60.,1,0);
                if(i>21)assertFalse(playback.visible("shell_main"));
                for(var matrix:playback.pose().matrices())for(float v:matrix.get(new float[16]))assertTrue(Float.isFinite(v));
            }
            playback.sample(2,false,2.1,4.1,1,.1);
            assertFalse(playback.visible("shell_main"));assertTrue(playback.visible("body_outline"));
        }
    }
}
