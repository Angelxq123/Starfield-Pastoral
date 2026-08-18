package com.stardew.craft.server;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PeriodicChunkAccessContractTest {

    @Test
    void jojaNpcPatrolNeverLoadsRemoteChunksSynchronously() throws IOException {
        Path projectDir = Path.of(System.getProperty("stardewcraft.projectDir"));
        String source = Files.readString(projectDir.resolve(
                "src/main/java/com/stardew/craft/joja/JojaNpcEvents.java"));

        assertFalse(source.contains("loadSpawnChunk"),
                "The periodic Joja patrol must not load remote chunks for unrelated players");
        assertFalse(source.contains("level.getChunk("),
                "Periodic NPC maintenance must not perform synchronous chunk loads");
        assertTrue(source.contains("getChunkNow"),
                "Joja NPC maintenance should run only when the spawn chunk is already loaded");
    }

    @Test
    void fishPondTickSkipsUnloadedRemotePonds() throws IOException {
        Path projectDir = Path.of(System.getProperty("stardewcraft.projectDir"));
        String source = Files.readString(projectDir.resolve(
                "src/main/java/com/stardew/craft/event/FishPondGameplayEvents.java"));

        assertTrue(source.contains("level.players().isEmpty()"),
                "Fish pond maintenance should stop when the dimension has no players");
        assertTrue(source.contains("isPondChunkLoaded(level, pond)"),
                "Fish pond maintenance must skip records whose bucket chunk is unloaded");
        assertTrue(source.contains("getChunkNow"),
                "Fish pond maintenance should use a non-blocking loaded-chunk check");
    }

    @Test
    void mutantBugLairMaintenanceNeverLoadsTheRewardChunkSynchronously() throws IOException {
        Path projectDir = Path.of(System.getProperty("stardewcraft.projectDir"));
        String source = Files.readString(projectDir.resolve(
                "src/main/java/com/stardew/craft/world/MutantBugLairService.java"));

        assertFalse(source.contains("level.getChunkAt(REWARD_CHEST_POS)"),
                "Periodic lair maintenance must not synchronously load its remote reward chunk");
        assertTrue(source.contains("getChunkNow"),
                "The reward chest should be maintained only while its chunk is loaded");
    }
}
