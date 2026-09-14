package com.stardew.craft.monster;

import com.google.gson.JsonParser;
import com.stardew.craft.mining.MineFloorData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class MonsterFoundationTest {
    private static final ResourceLocation ID = ResourceLocation.parse("stardewcraft:green_slime");
    private static final ResourceLocation SLIME = ResourceLocation.parse("stardewcraft:slime_item");
    private MonsterDefinition definition() {
        return new MonsterDefinition(ID, "Green Slime", "slime", 24, 5, 1, .05F, 3, true,
                List.of(new MonsterDefinition.Drop(SLIME, 1), new MonsterDefinition.Drop(SLIME, 1),
                        new MonsterDefinition.Drop(ResourceLocation.parse("stardewcraft:amethyst"), 0)));
    }
    private MonsterSpawnContext context(boolean bottom) {
        return new MonsterSpawnContext(MonsterSpawnContext.Source.ORDINARY_MINE, 32, bottom, UUID.randomUUID());
    }
    @Test void duplicateBornDropsSurviveSaveAndDoNotReroll() {
        var state = new MonsterState(definition(), context(true), RandomSource.create(7));
        var saved = state.save();
        var restored = MonsterState.load(saved);
        assertEquals(List.of(SLIME.toString(), SLIME.toString()), restored.bornDrops());
        assertEquals(saved, restored.save());
        assertEquals(24, restored.sourceMaxHealth());
        assertEquals(state.context(), restored.context());
    }
    @Test void everyFinalRewardChannelIsIndependentAndExactlyOnceEvenAfterReload() {
        var state = new MonsterState(definition(), context(false), RandomSource.create(1));
        assertFalse(state.claim(MonsterState.Settlement.WEAPON_REWARDS));
        state.life(MonsterState.Life.DOWNED);
        assertFalse(state.claim(MonsterState.Settlement.DROPS_AND_PROGRESS));
        state.life(MonsterState.Life.ALIVE);
        state.life(MonsterState.Life.DEAD);
        assertTrue(state.claim(MonsterState.Settlement.DROPS_AND_PROGRESS));
        var restored = MonsterState.load(state.save());
        assertFalse(restored.claim(MonsterState.Settlement.DROPS_AND_PROGRESS));
        assertTrue(restored.claim(MonsterState.Settlement.WEAPON_REWARDS));
        assertTrue(restored.claim(MonsterState.Settlement.POPULATION));
        assertFalse(restored.claim(MonsterState.Settlement.WEAPON_REWARDS));
        assertThrows(IllegalStateException.class, () -> restored.life(MonsterState.Life.ALIVE));
    }
    @Test void cleanupAndTransformationDoNotAwardKills() {
        for (var life : List.of(MonsterState.Life.CLEANUP, MonsterState.Life.TRANSFORMING)) {
            var state = new MonsterState(definition(), context(false), RandomSource.create(1));
            state.life(life);
            for (var channel : MonsterState.Settlement.values()) assertFalse(state.claim(channel));
        }
    }
    @Test void sourceProgressionUsesIntegerBoundsAndDoesNotScaleByFloor() {
        for (int seed = 0; seed < 100; seed++) {
            var base = MonsterStatResolver.base(definition(), context(false), RandomSource.create(seed));
            assertEquals(24, base.initialHealth()); assertEquals(5, base.combat().getDamage());
            var enhanced = MonsterStatResolver.base(definition(), context(true), RandomSource.create(seed));
            assertTrue(enhanced.initialHealth() >= 24 && enhanced.initialHealth() < 48);
            assertTrue(enhanced.combat().getDamage() >= 5 && enhanced.combat().getDamage() < 7);
            assertEquals(1, enhanced.combat().getResilience());
            assertEquals(.1F, enhanced.combat().getMissChance());
            assertEquals(3, enhanced.combat().getExperience());
        }
    }
    @Test void childrenKeepExactPopulationGenerationButCommandChildrenHaveNone() {
        var mine = context(true);
        assertEquals(mine.generation(), mine.offspring().generation());
        assertEquals(32, mine.offspring().floor());
        var command = new MonsterSpawnContext(MonsterSpawnContext.Source.COMMAND, 120, false, null);
        assertNull(command.offspring().generation());
        assertEquals(command, MonsterSpawnContext.load(command.save()));
    }
    @Test void floorRecreationChangesOwnerWhileLoadingKeepsOwner() {
        var floor = new MineFloorData();
        assertEquals(floor.generationId(), MineFloorData.fromNBT(floor.toNBT()).generationId());
        assertNotEquals(floor.generationId(), new MineFloorData().generationId());
    }
    @Test void effectDurationHalvesMillisecondsBeforeRoundingTicks() {
        var normal = com.stardew.craft.combat.equipment.EquipmentNegativeStatusProtection.millisecondsDecision(2550, false, false);
        var sturdy = com.stardew.craft.combat.equipment.EquipmentNegativeStatusProtection.millisecondsDecision(2550, false, true);
        assertEquals(51, normal.durationTicks());
        assertEquals(26, sturdy.durationTicks());
        assertTrue(com.stardew.craft.combat.equipment.EquipmentNegativeStatusProtection.millisecondsDecision(2550, true, true).resisted());
    }
    @Test void projectilePayloadKeepsItsOwnDamageInsteadOfContactStat() {
        var type = net.minecraft.core.Holder.direct(new net.minecraft.world.damagesource.DamageType("test", 0));
        var source = new MonsterDamageSource(new net.minecraft.world.damagesource.DamageSource(type), MonsterDamageSource.Kind.PROJECTILE, 17);
        assertEquals(17, MonsterDamageSource.resolveBaseDamage(source, 300));
        assertEquals(300, MonsterDamageSource.resolveBaseDamage(new net.minecraft.world.damagesource.DamageSource(type), 300));
    }
    @Test void shippedDefinitionUsesRegisteredItemsAndSourceNumbers() throws Exception {
        try (var input = getClass().getClassLoader().getResourceAsStream("data/stardewcraft/monsters/green_slime.json")) {
            assertNotNull(input);
            var definition = MonsterDefinition.parse(ID, JsonParser.parseReader(new java.io.InputStreamReader(input, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject());
            assertEquals(24, definition.health()); assertEquals(5, definition.damage()); assertEquals(3, definition.experience());
            for (var drop : definition.drops()) assertTrue(net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse(drop.item())), drop.item().toString());
        }
    }
    @Test void malformedDefinitionsCannotEnterTheSnapshot() {
        assertThrows(IllegalArgumentException.class, () -> new MonsterDefinition.Drop(SLIME, Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> new MonsterDefinition.Drop(SLIME, 1.1));
        assertThrows(IllegalArgumentException.class, () -> MonsterDefinition.parse(ID,
                JsonParser.parseString("{\"version\":2}").getAsJsonObject()));
    }
}
