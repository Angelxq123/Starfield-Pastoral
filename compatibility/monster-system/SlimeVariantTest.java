package com.stardew.craft.monster;

import com.google.gson.JsonParser;
import com.stardew.craft.entity.monster.SlimeVariant;
import com.stardew.craft.entity.monster.GreenSlimeRules;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import org.junit.jupiter.api.Test;
import java.io.InputStreamReader;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class SlimeVariantTest {
    private RandomSource fixed(double value) {
        return new LegacyRandomSource(1) {
            @Override public double nextDouble() { return value; }
            @Override public int nextInt(int bound) { return Math.min(bound - 1, (int) (value * bound)); }
        };
    }
    private MonsterDefinition definition(String id) throws Exception {
        try (var reader = new InputStreamReader(getClass().getResourceAsStream("/data/stardewcraft/monsters/" + id + ".json"))) {
            return MonsterDefinition.parse(ResourceLocation.parse("stardewcraft:" + id), JsonParser.parseReader(reader).getAsJsonObject());
        }
    }
    @Test void ordinaryColorRollsAndMarkedColorsMatchConstructorBranches() {
        assertEquals(0x26B3E4, SlimeVariant.FROST.rollColor(fixed(.5), 41));
        assertEquals(0xE41B26, SlimeVariant.SLUDGE.rollColor(fixed(.5), 81));
        assertEquals(0xB4FFC8, SlimeVariant.FROST.rollColor(fixed(0), 41));
        assertEquals(0, SlimeVariant.FROST.markedColor());
        assertEquals(0x230723, SlimeVariant.SLUDGE.markedColor());
        assertFalse(GreenSlimeRules.canBeMarked(40));
        assertFalse(GreenSlimeRules.canBeMarked(41));
        assertTrue(GreenSlimeRules.canBeMarked(42));
    }
    @Test void skullBoundaryAndOffspringColorClassificationAreDistinct() {
        assertFalse(SlimeVariant.SLUDGE.skull(120));
        assertTrue(SlimeVariant.SLUDGE.skull(121));
        assertEquals(0x8A2BE2, SlimeVariant.SLUDGE.rollColor(fixed(.5), 121));
        assertEquals(SlimeVariant.SLUDGE, SlimeVariant.offspringStats(0x8A2BE2));
        assertEquals(SlimeVariant.SLUDGE, SlimeVariant.offspringStats(0xE41B26));
        assertEquals(SlimeVariant.FROST, SlimeVariant.offspringStats(0x26B3E4));
        assertEquals(SlimeVariant.GREEN, SlimeVariant.offspringStats(0x55E01F));
    }
    @Test void frostRushIsExactlyItsQuarterBranch() {
        assertTrue(SlimeVariant.FROST.rushInsteadOfJump(.249999));
        assertFalse(SlimeVariant.FROST.rushInsteadOfJump(.25));
        assertFalse(SlimeVariant.GREEN.rushInsteadOfJump(0));
        assertFalse(SlimeVariant.SLUDGE.rushInsteadOfJump(0));
    }
    @Test void definitionsUseSourceStatsAndIndependentDropEntries() throws Exception {
        var frost = definition("frost_jelly"); var sludge = definition("sludge");
        assertEquals(List.of(106,7,0,6), List.of(frost.health(),frost.damage(),frost.resilience(),frost.experience()));
        assertEquals(List.of(205,16,0,10), List.of(sludge.health(),sludge.damage(),sludge.resilience(),sludge.experience()));
        assertEquals(132, GreenSlimeRules.health(frost.health(),false,true));
        assertEquals(256, GreenSlimeRules.health(sludge.health(),false,true));
        assertEquals(List.of("stardewcraft:slime_item","stardewcraft:winter_root","stardewcraft:jade",
                "stardewcraft:dwarf_scroll_iii","stardewcraft:sap","stardewcraft:dwarf_scroll_ii","stardewcraft:dwarf_scroll_iv"), frost.rollDrops(fixed(0)));
        assertEquals(List.of(.75,.08,.02,.015,.5,.005,.001), frost.drops().stream().map(MonsterDefinition.Drop::chance).toList());
        assertEquals(List.of(.8,.1,.1,.01,.5,.005,.001), sludge.drops().stream().map(MonsterDefinition.Drop::chance).toList());
        assertEquals("-4", sludge.drops().get(2).item());
    }
    @Test void coalDebrisTokenSurvivesSaveAndBurglarRollUntilMaterialization() throws Exception {
        var sludge = definition("sludge");
        var context = new MonsterSpawnContext(MonsterSpawnContext.Source.COMMAND,81,false,null);
        var original = new MonsterState(sludge,context,fixed(0));
        var restored = MonsterState.load(original.save());
        assertEquals(original.bornDrops(),restored.bornDrops());
        assertTrue(restored.bornDrops().contains("-4"));
        assertTrue(restored.rerollDrops(fixed(0)).contains("-4"));
        assertFalse(restored.rerollDrops(fixed(.999)).contains("-4"));
        assertEquals(1,MonsterSourceLoot.materialize(List.of("-4"),fixed(0)).getFirst().getCount());
        assertEquals(3,MonsterSourceLoot.materialize(List.of("-4"),fixed(.999)).getFirst().getCount());
    }
    @Test void variantsAreNativeTypesWithoutSpawnEggs() {
        assertNotEquals(net.minecraft.world.entity.EntityType.SLIME, SlimeVariant.FROST.type());
        assertNotEquals(net.minecraft.world.entity.EntityType.SLIME, SlimeVariant.SLUDGE.type());
        assertNull(net.minecraft.world.item.SpawnEggItem.byId(SlimeVariant.FROST.type()));
        assertNull(net.minecraft.world.item.SpawnEggItem.byId(SlimeVariant.SLUDGE.type()));
    }
}
