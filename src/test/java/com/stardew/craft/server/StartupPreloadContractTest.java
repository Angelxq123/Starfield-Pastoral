package com.stardew.craft.server;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import net.minecraft.world.level.ChunkPos;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StartupPreloadContractTest {
    private static final Path PROJECT = Path.of(System.getProperty("stardewcraft.projectDir", "."));

    @Test
    void mineLobbyAndFixedInteractionsAreRegisteredForStartupPreload() throws IOException {
        String source = source("src/main/java/com/stardew/craft/event/FixedPublicAreaStartupPreloader.java");

        assertTrue(source.contains("MineEntranceBootstrap.ensureGenerated"));
        assertTrue(source.contains("PrizeTicketMachineInstaller.ensurePlaced"));
        assertTrue(source.contains("CasinoAccessService.install"));
        assertTrue(source.contains("SystemTotemManager.installFixedTotems"));
    }

    @Test
    void jojaAndMutantBugLairCriticalChunksAreWarmedAtStartup() throws Exception {
        var method = com.stardew.craft.event.FixedPublicAreaStartupPreloader.class
                .getDeclaredMethod("fixedPublicChunks");
        method.setAccessible(true);
        @SuppressWarnings("unchecked")
        var chunks = Set.copyOf((List<ChunkPos>) method.invoke(null));

        assertTrue(chunks.containsAll(Set.of(
                new ChunkPos(7, -2),
                new ChunkPos(6, -2),
                new ChunkPos(0, 3),
                new ChunkPos(-1, 1),
                new ChunkPos(0, 1),
                new ChunkPos(0, -2))));

        String source = source("src/main/java/com/stardew/craft/event/FixedPublicAreaStartupPreloader.java");
        assertTrue(source.contains("JojaNpcEvents.forceCheckNow(valley)"));
        assertTrue(source.contains("MutantBugLairService.initializeLoadedFixedContent(valley)"));
    }

    @Test
    void fixedInstallersAreNotReplayedByPlayerLifecycleEvents() throws IOException {
        for (String relative : List.of(
                "shop/PrizeTicketMachineInstaller.java",
                "casino/CasinoAccessService.java",
                "qi/MrQiQuestInteractionService.java",
                "secretnote/SecretNote20Service.java",
                "world/OldMasterCannoliService.java")) {
            String source = source("src/main/java/com/stardew/craft/" + relative);
            assertFalse(source.contains("LevelEvent.Load"), relative);
            assertFalse(source.contains("PlayerChangedDimensionEvent"), relative);
        }
    }

    @Test
    void scheduledMerchantsNeverSynchronouslyLoadTheirSpawnChunk() throws IOException {
        for (String relative : List.of(
                "shop/CamelMerchantEvents.java",
                "shop/BooksellerEvents.java",
                "shop/TravelingCartEvents.java")) {
            assertFalse(source("src/main/java/com/stardew/craft/" + relative).contains(".getChunk("), relative);
        }
    }

    private static String source(String relativePath) throws IOException {
        return Files.readString(PROJECT.resolve(relativePath));
    }
}
