package com.stardew.craft.server;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import net.minecraft.world.level.ChunkPos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class WizardTowerEntryPerformanceContractTest {
    private static final Path PROJECT = Path.of(System.getProperty("stardewcraft.projectDir", "."));

    @Test
    void enteringValleyDoesNotReplayEveryPortal() throws IOException {
        String source = source("src/main/java/com/stardew/craft/event/InteriorSubspaceLifecycleEvents.java");

        assertFalse(source.contains("enter_stardew_dimension_safety_net"));
    }

    @Test
    void enteringValleyDoesNotInitializeDistantDesertChunks() throws IOException {
        String source = source("src/main/java/com/stardew/craft/event/InteriorSubspaceLifecycleEvents.java");

        assertFalse(source.contains("DesertMapBootstrap.ensureLoaded(player.serverLevel()"));
        assertFalse(source.contains("DesertGalaxyPillarBootstrap.ensurePlaced(player.serverLevel()"));
    }

    @Test
    void desertPillarMaintenanceNeverSynchronouslyLoadsItsChunk() throws IOException {
        String source = source("src/main/java/com/stardew/craft/desert/DesertGalaxyPillarBootstrap.java");

        assertFalse(source.contains("level.getChunkAt("));
        org.junit.jupiter.api.Assertions.assertTrue(source.contains("getChunkNow("));
    }

    @Test
    void desertPillarsAreCoveredByStartupPreload() throws IOException {
        String source = source("src/main/java/com/stardew/craft/event/FixedPublicAreaStartupPreloader.java");

        org.junit.jupiter.api.Assertions.assertTrue(
                source.contains("DesertGalaxyPillarBootstrap.RITUAL_TRIGGER_POS"));
        org.junit.jupiter.api.Assertions.assertTrue(
                source.contains("DesertGalaxyPillarBootstrap.ensurePlaced(valley"));
    }

    @Test
    void interiorPlayersDoNotActivateTheLegacyTownChunkKeeper() throws IOException {
        String source = source("src/main/java/com/stardew/craft/event/InteriorMainAreaChunkKeeperEvents.java");

        assertFalse(source.contains("LevelTickEvent"));
        assertFalse(source.contains("isPlayerInInteriorSpace"));
        assertFalse(source.contains("level.getChunk("));
    }

    @Test
    void normalValleyEntryDoesNotSynchronouslyPreloadTwentyFiveFarmChunks() throws IOException {
        String source = source("src/main/java/com/stardew/craft/event/DimensionEventHandler.java");

        assertFalse(source.contains("preloadChunksAround"));
        assertFalse(source.contains("level.getChunk(centerChunkX"));
    }

    @Test
    void wizardInteriorWarmsOnlyItsFourCriticalChunks() throws Exception {
        var method = com.stardew.craft.interior.CrossDimensionTeleporter.class
                .getDeclaredMethod("wizardInteriorWarmupChunks");
        method.setAccessible(true);
        @SuppressWarnings("unchecked")
        var actual = (java.util.List<ChunkPos>) method.invoke(null);

        assertEquals(
                Set.of(
                        new ChunkPos(-12, 3),
                        new ChunkPos(-11, 3),
                        new ChunkPos(-12, 4),
                        new ChunkPos(-11, 4)),
                Set.copyOf(actual));
    }

    @Test
    void wizardWarmupNeverUsesSynchronousChunkLoads() throws IOException {
        String source = source(
                "src/main/java/com/stardew/craft/interior/CrossDimensionTeleporter.java");

        assertFalse(source.contains(".getChunk("));
    }

    @Test
    void wizardEntryDoesNotForceTheNpcSystemInline() throws IOException {
        String source = source(
                "src/main/java/com/stardew/craft/interior/CrossDimensionTeleporter.java");

        assertFalse(source.contains("NpcSystem.forceTickNow"));
    }

    @Test
    void wizardReturnWaitsForItsOverworldChunkWithoutSynchronousLoading() throws IOException {
        String source = source(
                "src/main/java/com/stardew/craft/interior/CrossDimensionTeleporter.java");

        org.junit.jupiter.api.Assertions.assertTrue(source.contains("PENDING_WIZARD_RETURNS"));
        org.junit.jupiter.api.Assertions.assertTrue(source.contains("processPendingWizardReturns"));
        org.junit.jupiter.api.Assertions.assertTrue(source.contains("Queued {}'s overworld return"));
    }

    @Test
    void wizardInteriorUsesAPlayerScopedChunkTrackingRadius() throws IOException {
        String source = source(
                "src/main/java/com/stardew/craft/interior/CrossDimensionTeleporter.java");

        org.junit.jupiter.api.Assertions.assertTrue(
                source.contains("WIZARD_INTERIOR_VIEW_DISTANCE = 2"));
        org.junit.jupiter.api.Assertions.assertTrue(
                source.contains("applyWizardInteriorChunkTrackingView"));
        org.junit.jupiter.api.Assertions.assertTrue(
                source.contains("restoreDefaultChunkTrackingView"));
    }

    @Test
    void dimensionTravelDoesNotReplayChunkBackedFertilizerSnapshots() throws IOException {
        String source = source("src/main/java/com/stardew/craft/event/FertilizerSyncEvents.java");

        assertFalse(source.contains("PlayerChangedDimensionEvent"));
    }

    @Test
    void dimensionTravelDoesNotRebuildMuseumStandSnapshots() throws IOException {
        String source = source("src/main/java/com/stardew/craft/event/MuseumDonationSyncEvents.java");

        assertFalse(source.contains("PlayerChangedDimensionEvent"));
    }

    @Test
    void fixedPublicAreaInitializationStartsAtServerStartup() throws IOException {
        String source = source(
                "src/main/java/com/stardew/craft/event/InteriorSubspaceLifecycleEvents.java");

        org.junit.jupiter.api.Assertions.assertTrue(
                source.contains("DimensionEventHandler.scheduleDeferredInit(stardew);"));
    }

    @Test
    void playerTravelDoesNotStartFixedPublicAreaInitialization() throws IOException {
        String dimensionEvents = source(
                "src/main/java/com/stardew/craft/event/DimensionEventHandler.java");
        String playerDataEvents = source(
                "src/main/java/com/stardew/craft/player/PlayerDataEventHandler.java");

        assertFalse(dimensionEvents.contains("scheduleDeferredInit(level);"));
        assertFalse(playerDataEvents.contains("DimensionEventHandler.scheduleDeferredInit(player.serverLevel());"));
    }

    private static String source(String relativePath) throws IOException {
        return Files.readString(PROJECT.resolve(relativePath));
    }
}
