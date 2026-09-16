package com.stardew.craft.mining;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MineMonsterSpawnBalanceTest {
    @Test
    void safeFloorsRemainMonsterFree() {
        assertEquals(0, MineGenerationBalance.normalizeMonsterCount(0, 20, false));
        assertEquals(0, MineGenerationBalance.normalizeMonsterCount(1, 20, false));
        assertEquals(0, MineGenerationBalance.normalizeMonsterCount(10, 20, false));
        assertEquals(0, MineGenerationBalance.normalizeMonsterCount(120, 20, true));
    }

    @Test
    void regularMineFloorsHaveAControlledNonZeroPopulation() {
        assertEquals(2, MineGenerationBalance.normalizeMonsterCount(2, 0, false));
        assertEquals(5, MineGenerationBalance.normalizeMonsterCount(37, 5, false));
        assertEquals(8, MineGenerationBalance.normalizeMonsterCount(99, 40, false));
    }

    @Test
    void reusedRegularFloorRepairsAnEmptyPersistedPopulation() {
        assertEquals(2, MineGenerationBalance.expectedReusedFloorPopulation(5, 0));
        assertEquals(7, MineGenerationBalance.expectedReusedFloorPopulation(5, 7));
        assertEquals(0, MineGenerationBalance.expectedReusedFloorPopulation(10, 7));
    }

    @Test
    void generatedPopulationStaysInTheEntranceBand() {
        assertEquals(2, MineGenerationBalance.nearEntranceSpawnTarget(2));
        assertEquals(8, MineGenerationBalance.nearEntranceSpawnTarget(8));
    }

    @Test
    void skullCavernUsesAHigherControlledPopulation() {
        assertEquals(3, MineGenerationBalance.normalizeMonsterCount(121, 0, false));
        assertEquals(7, MineGenerationBalance.normalizeMonsterCount(150, 7, false));
        assertEquals(10, MineGenerationBalance.normalizeMonsterCount(200, 40, false));
    }

    @Test
    void monsterMuskDoublesTheClampedPopulationWithinBoostedCaps() {
        assertEquals(4, MineGenerationBalance.normalizeMonsterCount(2, 0, true));
        assertEquals(12, MineGenerationBalance.normalizeMonsterCount(37, 6, true));
        assertEquals(16, MineGenerationBalance.normalizeMonsterCount(99, 40, true));
        assertEquals(6, MineGenerationBalance.normalizeMonsterCount(121, 0, true));
        assertEquals(20, MineGenerationBalance.normalizeMonsterCount(200, 40, true));
    }

    @Test
    void entranceSpawnRingKeepsMonstersVisibleWithoutUsingTheSafeZone() {
        assertFalse(MineGenerationBalance.isNearEntranceSpawnCandidate(0, 7, 0, 0));
        assertTrue(MineGenerationBalance.isNearEntranceSpawnCandidate(0, 8, 0, 0));
        assertTrue(MineGenerationBalance.isNearEntranceSpawnCandidate(20, 15, 0, 0));
        assertTrue(MineGenerationBalance.isNearEntranceSpawnCandidate(0, 28, 0, 0));
        assertFalse(MineGenerationBalance.isNearEntranceSpawnCandidate(0, 29, 0, 0));
    }

    @Test
    void entranceSpawnBandRejectsHiddenVerticalCaveLayers() {
        assertTrue(MineGenerationBalance.isNearEntranceSpawnCandidate(
                0, 66, 8, 0, 66, 0));
        assertTrue(MineGenerationBalance.isNearEntranceSpawnCandidate(
                12, 62, 0, 0, 66, 0));
        assertFalse(MineGenerationBalance.isNearEntranceSpawnCandidate(
                12, 61, 0, 0, 66, 0));
        assertFalse(MineGenerationBalance.isNearEntranceSpawnCandidate(
                0, 66, 29, 0, 66, 0));
    }

    @Test
    void nativePopulationWaitsForChunkTrackingAndRejectsStaleGenerations() throws Exception {
        String source = source("mining/OrdinaryMinePopulation.java");
        assertTrue(source.contains("INITIAL_MONSTER_SPAWN_DELAY_TICKS=40"));
        assertTrue(source.contains("StardewSimulationTaskScheduler.schedule(level,"));
        assertTrue(source.contains("getFloorData(floor)!=data"));
        assertTrue(source.contains("submitMonsterPopulation(rejected,false)"));
        assertTrue(source.contains("if(mob!=null)data.addGeneratedMonster(mob.getUUID())"));
        assertFalse(source.contains("level.noCollision(mob)"));
    }

    @Test
    void nativeMonsterFactoryUsesCachedHybridSpawnBridge() throws Exception {
        String handler = source("event/MineMonsterSpawnHandler.java");
        String factory = source("monster/MonsterFactory.java");
        assertTrue(handler.contains("org.bukkit.event.entity.CreatureSpawnEvent$SpawnReason"));
        assertTrue(handler.contains("\"CUSTOM\""));
        assertTrue(handler.contains("youerSpawnBridgeResolved"));
        assertTrue(factory.contains("MineMonsterSpawnHandler.addWithSpawnReason(level,monster)"));
        assertTrue(handler.contains("mob.getRemovalReason()"));
        assertFalse(handler.contains("removalCallStack"));
    }

    @Test
    void nativePepperRexReplacesTheVanillaHoglinProxy() throws Exception {
        String source = source("event/MineMonsterSpawnHandler.java");
        assertTrue(source.contains("case\"pepper_rex\"->ModEntities.PEPPER_REX.get()"));
        assertTrue(source.contains("isRetiredMineMob(mob)"));
        assertFalse(source.contains("EntityType.HOGLIN"));
    }

    @Test
    void persistentNativeMineMonstersSurvivePeacefulDifficulty() throws Exception {
        String source = source("event/MineMonsterSpawnHandler.java");
        assertTrue(source.contains("MobDespawnEvent.Result.DENY"));
        assertTrue(source.contains("ModMiningDimensions.STARDEW_MINING.equals(mob.level().dimension())"));
        assertTrue(source.contains("mob.isPersistenceRequired()"));
        assertTrue(source.contains("mobinstanceofStardewMonsterEntity"));
    }

    private static String source(String relative) throws Exception {
        Path project = Path.of(System.getProperty("stardewcraft.projectDir", "."));
        return Files.readString(project.resolve("src/main/java/com/stardew/craft/" + relative))
                .replaceAll("\\s+", "");
    }
}
