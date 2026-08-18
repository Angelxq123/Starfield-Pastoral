package com.stardew.craft.network;

import com.stardew.craft.client.fishpond.ClientFishPondWaterColorCache;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FishPondColorSyncContractTest {
    private static final Path PROJECT_ROOT = Path.of(
            System.getProperty("stardewcraft.projectDir", "."));

    @AfterEach
    void clearClientCache() {
        ClientFishPondWaterColorCache.clearAll();
    }

    @Test
    void chunkSnapshotReplacesOnlyItsChunkAndDeltaRemovesStaleCells() throws Exception {
        String dimension = "stardewcraft:stardew_valley";
        BlockPos oldCell = new BlockPos(1, 64, 1);
        BlockPos retainedOtherChunk = new BlockPos(20, 64, 1);
        BlockPos replacement = new BlockPos(2, 64, 2);
        ClientFishPondWaterColorCache.replaceDimension(
                dimension, Map.of(oldCell, 0x112233, retainedOtherChunk, 0x445566));

        Method replaceChunk = method("replaceChunk");
        assertNotNull(replaceChunk, "chunk replacement method is missing");
        replaceChunk.invoke(null, dimension, 0, 0, Map.of(replacement, 0x778899));

        Map<Long, Integer> colors = ClientFishPondWaterColorCache.view().get(dimension);
        assertFalse(colors.containsKey(oldCell.asLong()));
        assertEquals(0x778899, colors.get(replacement.asLong()));
        assertEquals(0x445566, colors.get(retainedOtherChunk.asLong()));

        Method applyDelta = method("applyDelta");
        assertNotNull(applyDelta, "water-color delta method is missing");
        applyDelta.invoke(
                null,
                dimension,
                Map.of(oldCell, 0xABCDEF),
                java.util.List.of(replacement));

        colors = ClientFishPondWaterColorCache.view().get(dimension);
        assertEquals(0xABCDEF, colors.get(oldCell.asLong()));
        assertFalse(colors.containsKey(replacement.asLong()));
        assertEquals(0x445566, colors.get(retainedOtherChunk.asLong()));
    }

    @Test
    void serviceTargetsTrackingChunksWithoutDimensionWidePlayerLoops() throws IOException {
        String source = normalized(PROJECT_ROOT.resolve(
                "src/main/java/com/stardew/craft/fishpond/service/FishPondColorSyncService.java"));

        assertTrue(source.contains("sendChunkSnapshot(ServerPlayerplayer,ServerLevellevel,ChunkPoschunkPos)"));
        assertTrue(source.contains("syncPond(ServerLevellevel,FishPondRecordpond)"));
        assertTrue(source.contains("syncPondChange("));
        assertTrue(source.contains("PacketDistributor.sendToPlayersTrackingChunk("));
        assertFalse(source.contains("for(ServerPlayerplayer:level.players())"));
        assertFalse(source.contains("broadcastSnapshot("));
    }

    @Test
    void chunkWatchEventsReplaceAndClearClientChunkState() throws IOException {
        String source = normalized(PROJECT_ROOT.resolve(
                "src/main/java/com/stardew/craft/event/FishPondGameplayEvents.java"));

        assertTrue(source.contains("onChunkSent(ChunkWatchEvent.Sentevent)"));
        assertTrue(source.contains("FishPondColorSyncService.sendChunkSnapshot("));
        assertTrue(source.contains("onChunkUnwatched(ChunkWatchEvent.UnWatchevent)"));
        assertTrue(source.contains("FishPondColorSyncService.clearChunk("));
        assertFalse(source.contains("sendFullSnapshot("));
    }

    @Test
    void payloadSupportsChunkReplacementAndCellRemoval() throws IOException {
        String source = normalized(PROJECT_ROOT.resolve(
                "src/main/java/com/stardew/craft/network/payload/FishPondWaterColorSyncPayload.java"));

        assertTrue(source.contains("intchunkX"));
        assertTrue(source.contains("intchunkZ"));
        assertTrue(source.contains("booleanreplaceChunk"));
        assertTrue(source.contains("List<BlockPos>removedCells"));
        assertTrue(source.contains("ClientFishPondWaterColorCache.replaceChunk("));
        assertTrue(source.contains("ClientFishPondWaterColorCache.applyDelta("));
    }

    @Test
    void fishPondCodeNoLongerCallsDimensionWideSnapshotBroadcast() throws IOException {
        Path sourceRoot = PROJECT_ROOT.resolve("src/main/java/com/stardew/craft");
        try (var paths = Files.walk(sourceRoot)) {
            String matchingSource = paths
                    .filter(path -> path.toString().endsWith(".java"))
                    .map(path -> {
                        try {
                            return Files.readString(path);
                        } catch (IOException exception) {
                            throw new java.io.UncheckedIOException(exception);
                        }
                    })
                    .filter(source -> source.contains("FishPondColorSyncService.broadcastSnapshot"))
                    .findFirst()
                    .orElse("");
            assertTrue(matchingSource.isEmpty(), "dimension-wide fish pond broadcast remains");
        }
    }

    private static Method method(String name) {
        return Arrays.stream(ClientFishPondWaterColorCache.class.getDeclaredMethods())
                .filter(method -> method.getName().equals(name))
                .findFirst()
                .orElse(null);
    }

    private static String normalized(Path source) throws IOException {
        return Files.readString(source).replaceAll("\\s+", "");
    }
}
