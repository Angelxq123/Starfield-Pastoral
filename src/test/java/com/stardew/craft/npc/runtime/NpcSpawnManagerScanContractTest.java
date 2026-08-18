package com.stardew.craft.npc.runtime;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NpcSpawnManagerScanContractTest {

    @Test
    void loadedNpcScanDoesNotQueryTheWholeWorldAabb() throws IOException {
        Path projectDir = Path.of(System.getProperty("stardewcraft.projectDir"));
        String source = Files.readString(projectDir.resolve(
                "src/main/java/com/stardew/craft/npc/runtime/NpcSpawnManager.java"));

        assertFalse(source.contains("GLOBAL_NPC_SCAN"),
                "A world-border AABB makes the entity section lookup walk millions of sections");
        assertFalse(source.contains(
                        "getEntitiesOfClass(StardewNpcEntity.class, GLOBAL_NPC_SCAN)"),
                "NPC discovery must only iterate entities that are already loaded");
        assertTrue(source.contains("level.getAllEntities()"),
                "NPC discovery should use ServerLevel's loaded-entity iterable");
    }

    @Test
    void basementMonsterCleanupDoesNotQueryTheWholeWorldAabb() throws IOException {
        Path projectDir = Path.of(System.getProperty("stardewcraft.projectDir"));
        String source = Files.readString(projectDir.resolve(
                "src/main/java/com/stardew/craft/event/LuckyPurpleShortsWorldEvents.java"));

        assertFalse(source.contains("FULL_STARDEW_LEVEL_BOUNDS"),
                "Periodic basement cleanup must not scan the entire Stardew dimension");
        assertFalse(source.contains(
                        "getEntitiesOfClass(LuckyPurpleShortsMonsterEntity.class"),
                "Periodic cleanup must only inspect entities that are already loaded");
        assertTrue(source.contains("level.getAllEntities()"),
                "Periodic cleanup should use ServerLevel's loaded-entity iterable");
    }

    @Test
    void npcDebugCommandsDoNotQueryTheWholeWorldAabb() throws IOException {
        Path projectDir = Path.of(System.getProperty("stardewcraft.projectDir"));
        String source = Files.readString(projectDir.resolve(
                "src/main/java/com/stardew/craft/command/NpcDebugCommand.java"));

        assertFalse(source.contains("GLOBAL_NPC_SCAN"),
                "An administrator debug command must not freeze the server with a world-border scan");
        assertTrue(source.contains("level.getAllEntities()"),
                "NPC diagnostics should inspect only entities that are already loaded");
    }
}
