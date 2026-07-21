package com.stardew.craft.network;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientContentSyncServiceContractTest {
    private static final Path SOURCE = Path.of(System.getProperty("stardewcraft.projectDir", "."))
            .resolve("src/main/java/com/stardew/craft/network/ClientContentSyncService.java");

    @Test
    void perRecipientOperationsPreserveSendOrderAndCountCompletedCalls() throws IOException {
        String loop = normalizedRecipientLoop();

        assertOrdered(loop,
                "PacketDistributor.sendToPlayer(player,registrySnapshot);",
                "ServerPerformanceRecorder.increment(PerformanceCounter.CONTENT_SYNC_PACKETS,1L);",
                "ServerPerformanceRecorder.increment(PerformanceCounter.CONTENT_REGISTRY_BYTES,"
                        + "registrySnapshot.estimatedEncodedBytes());",
                "PacketDistributor.sendToPlayer(player,mailSnapshot);",
                "ServerPerformanceRecorder.increment(PerformanceCounter.CONTENT_SYNC_PACKETS,1L);",
                "PacketDistributor.sendToPlayer(player,festivalSnapshot);",
                "ServerPerformanceRecorder.increment(PerformanceCounter.CONTENT_SYNC_PACKETS,1L);",
                "JeiCatalogSyncPayloadjeiSnapshot=ServerPerformanceRecorder.measure(",
                "ServerPerformanceRecorder.increment(PerformanceCounter.JEI_CATALOG_ENTRIES,",
                "PacketDistributor.sendToPlayer(player,jeiSnapshot);",
                "ServerPerformanceRecorder.increment(PerformanceCounter.CONTENT_SYNC_PACKETS,1L);");
    }

    @Test
    void recipientSelectionDoesNotPrecountPacketsOrRegistryBytes() throws IOException {
        String source = normalizedSource();
        int loopStart = source.indexOf("for(ServerPlayerplayer:recipients)");
        assertTrue(loopStart >= 0, "recipient loop is missing");
        String beforeLoop = source.substring(0, loopStart);

        assertFalse(beforeLoop.contains("PerformanceCounter.CONTENT_SYNC_PACKETS"));
        assertFalse(beforeLoop.contains("PerformanceCounter.CONTENT_REGISTRY_BYTES"));
    }

    private static String normalizedRecipientLoop() throws IOException {
        String source = normalizedSource();
        int loopStart = source.indexOf("for(ServerPlayerplayer:recipients)");
        int loopEnd = source.indexOf("StardewCraft.LOGGER.info", loopStart);
        assertTrue(loopStart >= 0 && loopEnd > loopStart, "recipient loop is missing");
        return source.substring(loopStart, loopEnd);
    }

    private static String normalizedSource() throws IOException {
        return Files.readString(SOURCE).replaceAll("\\s+", "");
    }

    private static void assertOrdered(String source, String... operations) {
        int cursor = 0;
        for (String operation : operations) {
            int operationIndex = source.indexOf(operation, cursor);
            assertTrue(operationIndex >= cursor, () -> "missing or out-of-order operation: " + operation);
            cursor = operationIndex + operation.length();
        }
    }
}
