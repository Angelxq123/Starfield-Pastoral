package com.stardew.craft.server;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoginNetworkBurstContractTest {
    private static final Path PROJECT = Path.of(System.getProperty("stardewcraft.projectDir", "."));

    @Test
    void contentSnapshotIsStagedAcrossServerTicks() throws IOException {
        String source = source("network/ClientContentSyncService.java");
        String cutscenes = source("cutscene/CutsceneSystem.java");
        String syncHandler = between(source, "private static void sync(", "private static SharedSnapshot buildSharedSnapshot");

        assertTrue(source.contains("MAX_PLAYERS_PER_TICK = 4"));
        assertTrue(source.contains("PENDING_SYNCS"));
        assertTrue(source.contains("onServerTick(ServerTickEvent.Post event)"));
        assertTrue(source.contains("int jobsThisTick = Math.min(MAX_PLAYERS_PER_TICK, PENDING_SYNCS.size())"));
        assertTrue(source.contains("SyncEventRegistryPayload.current()"));
        assertFalse(syncHandler.contains("PacketDistributor.sendToPlayer"));
        assertFalse(cutscenes.contains("SyncEventRegistryPayload.current()"));
    }

    @Test
    void friendshipOverviewIsSentOnlyOnceOnLogin() throws IOException {
        String playerData = source("player/PlayerDataEventHandler.java");
        String loginSync = source("network/PlayerLoginSyncService.java");
        String cutscenes = source("cutscene/CutsceneSystem.java");

        assertEquals(0, occurrences(playerData, "RequestNpcFriendshipOverviewPayload.sendOverviewTo(player)"));
        assertEquals(1, occurrences(loginSync, "RequestNpcFriendshipOverviewPayload.sendOverviewTo(player)"));
        assertEquals(0, occurrences(cutscenes, "RequestNpcFriendshipOverviewPayload.sendOverviewTo(player)"));
    }

    @Test
    void fertilizerUsesChunkWatchWithoutDelayedLoginReplayOrEmptySnapshots() throws IOException {
        String events = source("event/FertilizerSyncEvents.java");
        String manager = source("manager/FertilizerManager.java");

        assertFalse(events.contains("syncAllFertilizersToPlayer"));
        assertTrue(manager.contains("if (snapshot.isEmpty())"));
    }

    @Test
    void completePlayerDataSyncSkipsOnlyIdenticalSnapshotsAndResetsByLifecycle() throws IOException {
        String playerData = source("player/PlayerDataEventHandler.java");
        String packet = source("network/PlayerDataSyncPacket.java");

        assertTrue(playerData.contains("PlayerDataSyncSnapshotCache"));
        assertTrue(playerData.contains("buildFullSyncSnapshot"));
        assertTrue(playerData.contains("if (FULL_SYNC_SNAPSHOTS.shouldSend(player.getUUID(), snapshot))"));
        assertTrue(playerData.contains("FULL_SYNC_SNAPSHOTS.clear(player.getUUID())"));
        assertTrue(playerData.contains("FULL_SYNC_SNAPSHOTS.clearAll()"));
        assertTrue(packet.contains("clientView(playerData.toNBT())"));
    }

    private static String source(String relativePath) throws IOException {
        return Files.readString(PROJECT.resolve("src/main/java/com/stardew/craft").resolve(relativePath));
    }

    private static String between(String source, String start, String end) {
        int from = source.indexOf(start);
        int to = source.indexOf(end, from);
        assertTrue(from >= 0 && to > from);
        return source.substring(from, to);
    }

    private static int occurrences(String source, String value) {
        int count = 0;
        int cursor = 0;
        while ((cursor = source.indexOf(value, cursor)) >= 0) {
            count++;
            cursor += value.length();
        }
        return count;
    }
}
