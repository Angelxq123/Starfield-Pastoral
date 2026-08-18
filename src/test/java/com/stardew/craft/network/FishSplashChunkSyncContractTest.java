package com.stardew.craft.network;

import com.stardew.craft.client.fishing.ClientFishSplashState;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FishSplashChunkSyncContractTest {
    private static final Path PROJECT_ROOT = Path.of(
            System.getProperty("stardewcraft.projectDir", "."));

    @AfterEach
    void clearClientState() {
        ClientFishSplashState.clearAll();
    }

    @Test
    void chunkSnapshotOnlyReplacesSplashPointsInsideThatChunk() throws Exception {
        BlockPos oldPoint = new BlockPos(1, 64, 1);
        BlockPos otherChunkPoint = new BlockPos(20, 64, 1);
        BlockPos replacement = new BlockPos(2, 64, 2);
        ClientFishSplashState.replaceAll(Map.of(
                "Forest", oldPoint,
                "Beach", otherChunkPoint));

        Method replaceChunk = Arrays.stream(ClientFishSplashState.class.getDeclaredMethods())
                .filter(method -> method.getName().equals("replaceChunk"))
                .findFirst()
                .orElse(null);
        assertNotNull(replaceChunk, "fish splash chunk replacement is missing");
        replaceChunk.invoke(null, 0, 0, Map.of("Mountain", replacement));

        assertFalse(ClientFishSplashState.view().containsKey("Forest"));
        assertEquals(replacement, ClientFishSplashState.view().get("Mountain"));
        assertEquals(otherChunkPoint, ClientFishSplashState.view().get("Beach"));
    }

    @Test
    void splashStateTargetsTrackingChunksAndHandlesMoves() throws IOException {
        String source = normalized(PROJECT_ROOT.resolve(
                "src/main/java/com/stardew/craft/fishing/splash/FishSplashState.java"));

        assertTrue(source.contains("sendChunkSnapshot(ServerPlayerplayer,ChunkPoschunkPos)"));
        assertTrue(source.contains("syncChange("));
        assertTrue(source.contains("PacketDistributor.sendToPlayersTrackingChunk("));
        assertTrue(source.contains("previousChunk.equals(currentChunk)"));
        assertFalse(source.contains("for(ServerPlayerp:stardewLevel.players())"));
        assertFalse(source.contains("sendFullSnapshot("));
        assertFalse(source.contains("broadcastChange("));
    }

    @Test
    void chunkEventsDriveSnapshotsAndDisconnectClearsClientCache() throws IOException {
        Path events = PROJECT_ROOT.resolve(
                "src/main/java/com/stardew/craft/event/FishSplashSyncEvents.java");
        assertTrue(Files.exists(events), "fish splash chunk event handler is missing");
        String source = normalized(events);

        assertTrue(source.contains("onChunkSent(ChunkWatchEvent.Sentevent)"));
        assertTrue(source.contains("FishSplashState.get(level).sendChunkSnapshot("));
        assertTrue(source.contains("onChunkUnwatched(ChunkWatchEvent.UnWatchevent)"));
        assertTrue(source.contains("ClientFishSplashState.clearAll()"));
    }

    @Test
    void payloadSupportsFullDebugFallbackAndChunkReplacement() throws IOException {
        String source = normalized(PROJECT_ROOT.resolve(
                "src/main/java/com/stardew/craft/network/payload/FishSplashSyncPayload.java"));

        assertTrue(source.contains("booleanfullSnapshot"));
        assertTrue(source.contains("booleanchunkSnapshot"));
        assertTrue(source.contains("intchunkX"));
        assertTrue(source.contains("intchunkZ"));
        assertTrue(source.contains("ClientFishSplashState.replaceChunk("));
        assertTrue(source.contains("staticFishSplashSyncPayloadchunkSnapshot("));
    }

    @Test
    void loginNoLongerPushesSplashFullSnapshot() throws IOException {
        String source = normalized(PROJECT_ROOT.resolve(
                "src/main/java/com/stardew/craft/player/PlayerDataEventHandler.java"));

        assertFalse(source.contains("FishSplashState.getStardewState("));
        assertFalse(source.contains("sendFullSnapshot(player)"));
    }

    private static String normalized(Path source) throws IOException {
        return Files.readString(source).replaceAll("\\s+", "");
    }
}
