package com.stardew.craft.network;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BundleSyncPayloadContractTest {
    private static final Path SOURCE_ROOT = Path.of(
            System.getProperty("stardewcraft.projectDir", "."),
            "src/main/java/com/stardew/craft/communitycenter/network");

    @Test
    void fixedDefinitionsAreSeparatedFromPlayerProgress() throws IOException {
        Path definitions = SOURCE_ROOT.resolve("BundleDefinitionSyncPayload.java");
        assertTrue(Files.exists(definitions), "bundle definition payload is missing");

        String definitionSource = normalized(definitions);
        String progressSource = normalized(SOURCE_ROOT.resolve("BundleSyncPayload.java"));

        assertTrue(definitionSource.contains("List<BundleDefinition>definitions"));
        assertTrue(definitionSource.contains("Map<Integer,String>areaNames"));
        assertTrue(definitionSource.contains("BundleDataManager.applyFromNetwork("));
        assertFalse(progressSource.contains("List<BundleDefinition>definitions"));
        assertFalse(progressSource.contains("BundleDataManager.applyFromNetwork("));
    }

    @Test
    void unchangedDefinitionsAreNotResentWithFrequentProgressUpdates() throws IOException {
        String definitionSource = normalized(SOURCE_ROOT.resolve("BundleDefinitionSyncPayload.java"));
        String progressSource = normalized(SOURCE_ROOT.resolve("BundleSyncPayload.java"));

        assertTrue(definitionSource.contains("sendIfChanged(ServerPlayerplayer"));
        assertTrue(definitionSource.contains("previous.equals(snapshot)"));
        assertTrue(progressSource.contains(
                "BundleDefinitionSyncPayload.sendIfChanged(player,resolvedDefinitions)"));
        assertTrue(progressSource.indexOf("BundleDefinitionSyncPayload.sendIfChanged(")
                < progressSource.indexOf("PacketDistributor.sendToPlayer(player,newBundleSyncPayload("));
    }

    @Test
    void definitionCacheIsScopedAndClearedAcrossPlayerAndServerLifecycles() throws IOException {
        String source = normalized(SOURCE_ROOT.resolve("BundleDefinitionSyncPayload.java"));

        assertTrue(source.contains("Map<MinecraftServer,Map<UUID,Snapshot>>"));
        assertTrue(source.contains("onPlayerLogout(PlayerEvent.PlayerLoggedOutEventevent)"));
        assertTrue(source.contains("remove(event.getEntity().getUUID())"));
        assertTrue(source.contains("onServerStopped(ServerStoppedEventevent)"));
        assertTrue(source.contains("SENT_SNAPSHOTS.remove(event.getServer())"));
    }

    private static String normalized(Path source) throws IOException {
        if (!Files.exists(source)) {
            return "";
        }
        return Files.readString(source).replaceAll("\\s+", "");
    }
}
