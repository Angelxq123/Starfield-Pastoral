package com.stardew.craft.farm;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OfflineFarmCatchUpContractTest {
    private static final Path PROJECT =
            Path.of(System.getProperty("stardewcraft.projectDir", "."));

    @Test
    void loginEntryOnlyEnqueuesCatchUpInsteadOfRunningItSynchronously() throws IOException {
        String catchUp = normalized("farm/OfflineFarmCatchUp.java");
        String players = normalized("player/PlayerDataEventHandler.java");

        assertTrue(catchUp.contains(
                "publicstaticvoidcatchUp(ServerLevellevel,UUIDplayerUUID){OfflineFarmCatchUpService.enqueue(level,playerUUID);}"));
        assertFalse(catchUp.contains("getAllCropPositions"));
        assertFalse(catchUp.contains("acquireTemporaryChunks"));
        assertTrue(players.contains("OfflineFarmCatchUp.catchUp(stardewLevel,player.getUUID())"));
    }

    @Test
    void serverQueueTicksWithinLimitsAndPausesDuringDailySettlement() throws IOException {
        String service = normalized("farm/OfflineFarmCatchUpService.java");

        assertTrue(service.contains("@SubscribeEventpublicstaticvoidonServerTick(ServerTickEvent.Postevent)"));
        assertTrue(service.contains("coordinator().isActive()"));
        assertTrue(service.contains("Config.DAILY_SETTLEMENT_BUDGET_MILLIS.get()"));
        assertTrue(service.contains("Config.DAILY_SETTLEMENT_ITEM_LIMIT.get()"));
        assertTrue(service.contains("jobsByOwner"));
        assertTrue(service.contains("pendingByOwner"));
        assertTrue(service.contains("createPendingJob("));
        assertTrue(service.contains("Catch-upjobcreationexceededtickbudget"));
        assertTrue(service.contains("getOwnerForPlayer(playerId)"));
    }

    @Test
    void jobSnapshotsFarmIndexesAndLeasesOnlyTheCurrentItemFootprint() throws IOException {
        String service = normalized("farm/OfflineFarmCatchUpService.java");

        assertTrue(service.contains("OfflineFarmCatchUpPlan.create("));
        assertTrue(service.contains("cropMgr.getAllCropPositions()"));
        assertTrue(service.contains("treeMgr.getAllSaplingPositions()"));
        assertTrue(service.contains("sprinklerMgr.getAllSprinklerPositions()"));
        assertTrue(service.contains("acquireTemporaryChunks(level,FarmChunkManager.chunkPositionsForPosition(position.pos(),radius))"));
        assertFalse(service.contains("plan.requiredChunks()"));
    }

    @Test
    void everyCompletedDayPersistsItsFarmCursor() throws IOException {
        String service = normalized("farm/OfflineFarmCatchUpService.java");

        assertTrue(service.contains("if(farm.getLastOnlineDay()<absoluteDay)"));
        assertTrue(service.contains("farm.setLastOnlineDay(absoluteDay)"));
        assertTrue(service.contains("farm.setLastOnlineSeason(OfflineFarmCatchUp.seasonOfAbsDay(absoluteDay))"));
        assertTrue(service.contains("registry.setDirty()"));
    }

    @Test
    void gameplayAndPayloadGuardsIncludeTheCatchUpLock() throws IOException {
        String events = normalized("time/settlement/DailySettlementEvents.java");
        String guard = normalized("time/settlement/DailySettlementAccessGuard.java");

        assertTrue(events.contains("OfflineFarmCatchUpService.isPlayerLocked(player)"));
        assertTrue(guard.contains("OfflineFarmCatchUpService.isPlayerLocked(player)"));
    }

    @Test
    void pendingOvernightSettlementDefersCatchUpUntilAcknowledgement() throws IOException {
        String service = normalized("farm/OfflineFarmCatchUpService.java");
        String events = normalized("time/settlement/DailySettlementEvents.java");

        assertTrue(service.contains("DailySettlementServices.ownsSettlement("));
        assertTrue(events.contains("OfflineFarmCatchUp.catchUp(stardewLevel,player.getUUID())"));
    }

    private static String normalized(String relative) throws IOException {
        return Files.readString(PROJECT.resolve("src/main/java/com/stardew/craft/").resolve(relative))
                .replaceAll("\\s+", "");
    }
}
