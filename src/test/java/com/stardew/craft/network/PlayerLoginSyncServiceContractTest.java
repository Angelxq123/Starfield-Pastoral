package com.stardew.craft.network;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerLoginSyncServiceContractTest {
    private static final Path PROJECT_ROOT = Path.of(
            System.getProperty("stardewcraft.projectDir", "."));

    @Test
    void loginSnapshotsAreRoundRobinStagedUnderAGlobalTickBudget() throws IOException {
        Path service = PROJECT_ROOT.resolve(
                "src/main/java/com/stardew/craft/network/PlayerLoginSyncService.java");
        assertTrue(Files.exists(service), "player login sync service is missing");
        String source = normalized(service);

        assertTrue(source.contains("MAX_STAGES_PER_TICK=4"));
        assertTrue(source.contains("Queue<PendingSync>PENDING_SYNCS"));
        assertTrue(source.contains("Math.min(MAX_STAGES_PER_TICK,PENDING_SYNCS.size())"));
        assertTrue(source.contains("PendingSyncpending=PENDING_SYNCS.poll()"));
        assertTrue(source.contains("if(pending.sendNext(player))"));
        assertTrue(source.contains("PENDING_SYNCS.add(pending)"));
    }

    @Test
    void eachStageSendsOneLogicalPlayerSnapshot() throws IOException {
        String source = normalized(PROJECT_ROOT.resolve(
                "src/main/java/com/stardew/craft/network/PlayerLoginSyncService.java"));

        assertOrdered(source,
                "caseCOSMETICS->",
                "CosmeticAppearanceSync.syncAllTo(player)",
                "stage=SyncStage.FESTIVAL_SESSIONS",
                "caseFESTIVAL_SESSIONS->",
                "StardewFestivalSessionSyncService.syncToPlayer(player)",
                "stage=SyncStage.COMMUNITY_CENTER",
                "caseCOMMUNITY_CENTER->",
                "BundleSyncPayload.sendFullSync(player)",
                "stage=SyncStage.PAN_POINT",
                "casePAN_POINT->",
                "OrePanPointManager.get(player.serverLevel()).syncToClient(player)",
                "stage=SyncStage.NPC_FRIENDSHIP",
                "caseNPC_FRIENDSHIP->",
                "RequestNpcFriendshipOverviewPayload.sendOverviewTo(player)",
                "stage=SyncStage.QUEST_LOG",
                "caseQUEST_LOG->",
                "QuestLogSyncPayload.fromQuests(",
                "stage=SyncStage.SPECIAL_ORDERS",
                "caseSPECIAL_ORDERS->",
                "SpecialOrderManager.syncState(player)");
    }

    @Test
    void loginHandlerQueuesSnapshotsInsteadOfSendingThemInline() throws IOException {
        String source = normalized(PROJECT_ROOT.resolve(
                "src/main/java/com/stardew/craft/player/PlayerDataEventHandler.java"));

        assertTrue(source.contains("PlayerLoginSyncService.enqueue(player)"));
        assertFalse(source.contains("CosmeticAppearanceSync.syncAllTo(player)"));
        assertFalse(source.contains("StardewFestivalSessionSyncService.syncToPlayer(player)"));
        assertFalse(source.contains("BundleSyncPayload.sendFullSync(player)"));
        assertFalse(source.contains("OrePanPointManager.get(sl).syncToClient(player)"));
        assertFalse(source.contains("RequestNpcFriendshipOverviewPayload.sendOverviewTo(player)"));
        assertFalse(source.contains("QuestLogSyncPayload.fromQuests("));
        assertFalse(source.contains("SpecialOrderManager.syncState(player)"));
    }

    @Test
    void existingPlayerCosmeticsAreBatchedIntoOnePacketForTheJoiner() throws IOException {
        Path payload = PROJECT_ROOT.resolve(
                "src/main/java/com/stardew/craft/network/payload/CosmeticAppearanceBatchSyncPayload.java");
        assertTrue(Files.exists(payload), "cosmetic appearance batch payload is missing");
        String payloadSource = normalized(payload);
        String syncSource = normalized(PROJECT_ROOT.resolve(
                "src/main/java/com/stardew/craft/player/CosmeticAppearanceSync.java"));
        String packetHandler = normalized(PROJECT_ROOT.resolve(
                "src/main/java/com/stardew/craft/network/PacketHandler.java"));

        assertTrue(payloadSource.contains("List<Appearance>appearances"));
        assertTrue(payloadSource.contains("ClientPlayerDataCache.setCosmeticAppearance("));
        assertTrue(syncSource.contains("CosmeticAppearanceBatchSyncPayload"));
        assertTrue(syncSource.contains("PacketDistributor.sendToPlayer(recipient,"));
        assertFalse(syncSource.contains("sendToPlayer(recipient,subject,"));
        assertTrue(packetHandler.contains("CosmeticAppearanceBatchSyncPayload.TYPE"));
    }

    @Test
    void logoutAndServerStopDiscardPendingWork() throws IOException {
        String source = normalized(PROJECT_ROOT.resolve(
                "src/main/java/com/stardew/craft/network/PlayerLoginSyncService.java"));

        assertTrue(source.contains("onPlayerLogout(PlayerEvent.PlayerLoggedOutEventevent)"));
        assertTrue(source.contains("pending.playerId.equals(event.getEntity().getUUID())"));
        assertTrue(source.contains("onServerStopped(ServerStoppedEventevent)"));
        assertTrue(source.contains("pending.server==event.getServer()"));
    }

    @Test
    void queueWorkIsVisibleInPerformanceTelemetry() throws IOException {
        String source = normalized(PROJECT_ROOT.resolve(
                "src/main/java/com/stardew/craft/network/PlayerLoginSyncService.java"));
        String timings = normalized(PROJECT_ROOT.resolve(
                "src/main/java/com/stardew/craft/server/performance/PerformanceTiming.java"));
        String counters = normalized(PROJECT_ROOT.resolve(
                "src/main/java/com/stardew/craft/server/performance/PerformanceCounter.java"));

        assertTrue(source.contains("PerformanceTiming.PLAYER_LOGIN_SYNC_STAGE"));
        assertTrue(source.contains("PerformanceCounter.PLAYER_LOGIN_SYNC_ENQUEUED"));
        assertTrue(source.contains("PerformanceCounter.PLAYER_LOGIN_SYNC_STAGES"));
        assertTrue(source.contains("PerformanceCounter.PLAYER_LOGIN_SYNC_COMPLETED"));
        assertTrue(timings.contains("PLAYER_LOGIN_SYNC_STAGE"));
        assertTrue(counters.contains("PLAYER_LOGIN_SYNC_ENQUEUED"));
        assertTrue(counters.contains("PLAYER_LOGIN_SYNC_STAGES"));
        assertTrue(counters.contains("PLAYER_LOGIN_SYNC_COMPLETED"));
    }

    private static String normalized(Path source) throws IOException {
        if (!Files.exists(source)) {
            return "";
        }
        return Files.readString(source).replaceAll("\\s+", "");
    }

    private static void assertOrdered(String source, String... operations) {
        int cursor = 0;
        for (String operation : operations) {
            int found = source.indexOf(operation, cursor);
            assertTrue(found >= cursor, () -> "missing or out-of-order operation: " + operation);
            cursor = found + operation.length();
        }
    }
}
