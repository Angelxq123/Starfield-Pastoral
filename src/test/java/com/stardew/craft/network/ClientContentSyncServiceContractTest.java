package com.stardew.craft.network;

import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientContentSyncServiceContractTest {
    private static final Path SOURCE = Path.of(System.getProperty("stardewcraft.projectDir", "."))
            .resolve("src/main/java/com/stardew/craft/network/ClientContentSyncService.java");

    @Test
    void sharedContentCacheAndSnapshotHaveTheRequiredTypes() throws ReflectiveOperationException {
        Class<?> sharedSnapshot = Arrays.stream(ClientContentSyncService.class.getDeclaredClasses())
                .filter(candidate -> candidate.getSimpleName().equals("SharedSnapshot"))
                .findFirst()
                .orElseThrow();
        Field cache = ClientContentSyncService.class.getDeclaredField("SHARED_CONTENT");

        assertTrue(sharedSnapshot.isRecord());
        assertTrue(Modifier.isPrivate(sharedSnapshot.getModifiers()));
        assertArrayEquals(
                new Class<?>[]{
                        DataRegistrySyncPayload.class,
                        int.class,
                        com.stardew.craft.cutscene.network.SyncEventRegistryPayload.class,
                        MailIndexSyncPayload.class,
                        JeiCatalogSyncPayload.SharedCatalog.class
                },
                Arrays.stream(sharedSnapshot.getRecordComponents()).map(component -> component.getType()).toArray());
        assertTrue(Modifier.isPrivate(cache.getModifiers()));
        assertTrue(Modifier.isStatic(cache.getModifiers()));
        assertTrue(Modifier.isFinal(cache.getModifiers()));
        assertEquals(ClientContentSnapshotCache.class, cache.getType());

        ParameterizedType cacheType = (ParameterizedType) cache.getGenericType();
        assertArrayEquals(
                new java.lang.reflect.Type[]{MinecraftServer.class, sharedSnapshot},
                cacheType.getActualTypeArguments());
    }

    @Test
    void reloadForcesRebuildAndLoginLazilyReusesTheServerSnapshot() throws IOException {
        String handler = datapackSyncHandler();

        assertOrdered(handler,
                "MinecraftServerserver=event.getPlayerList().getServer();",
                "event.getPlayer()==null",
                "SHARED_CONTENT.rebuild(server,ClientContentSyncService::buildSharedSnapshot)",
                "SHARED_CONTENT.getOrBuild(server,ClientContentSyncService::buildSharedSnapshot)",
                "SharedSnapshotshared=cached.value();",
                "FestivalAvailabilitySyncPayloadfestivalSnapshot="
                        + "FestivalAvailabilitySyncPayload.current();",
                "List<ServerPlayer>recipients=event.getRelevantPlayers().toList();");
        assertEquals(1, occurrences(handler, "event.getRelevantPlayers().toList()"));
    }

    @Test
    void sharedBuildMeasuresRegistrySizeCutscenesMailAndGlobalJeiTogetherOnce() throws IOException {
        String source = normalizedSource();
        String builder = substringBetween(
                source,
                "privatestaticSharedSnapshotbuildSharedSnapshot(longgeneration)",
                "privaterecordSharedSnapshot(");

        assertEquals(1, occurrences(source, "PerformanceTiming.CONTENT_SNAPSHOT_BUILD"));
        assertEquals(1, occurrences(builder, "PerformanceTiming.CONTENT_SNAPSHOT_BUILD"));
        assertOrdered(builder,
                "ServerPerformanceRecorder.measure(PerformanceTiming.CONTENT_SNAPSHOT_BUILD,()->{",
                "DataRegistrySyncPayloadregistry=DataRegistrySyncPayload.current();",
                "registry.estimatedEncodedBytes()",
                "SyncEventRegistryPayload.current()",
                "MailIndexSyncPayload.current()",
                "JeiCatalogSyncPayload.currentSharedCatalog()",
                "});");
        assertFalse(builder.contains("FestivalAvailabilitySyncPayload"));
    }

    @Test
    void festivalAvailabilityRemainsLiveOutsideTheSharedBuilder() throws IOException {
        String source = normalizedSource();
        String handler = datapackSyncHandler();

        assertEquals(1, occurrences(source, "FestivalAvailabilitySyncPayload.current()"));
        assertOrdered(handler,
                "SharedSnapshotshared=cached.value();",
                "FestivalAvailabilitySyncPayload.current()",
                "event.getRelevantPlayers().toList()");
    }

    @Test
    void sharedSnapshotValidatesPayloadsAndEncodedSize() throws IOException {
        String source = normalizedSource();
        String snapshot = substringBetween(
                source,
                "privaterecordSharedSnapshot(",
                "@SubscribeEventpublicstaticvoidonServerStopped(");

        assertTrue(snapshot.contains("Objects.requireNonNull(registry,\"registry\")"));
        assertTrue(snapshot.contains("if(registryEncodedBytes<0)"));
        assertTrue(snapshot.contains("thrownewIllegalArgumentException("));
        assertTrue(snapshot.contains("Objects.requireNonNull(cutscenes,\"cutscenes\")"));
        assertTrue(snapshot.contains("Objects.requireNonNull(mail,\"mail\")"));
        assertTrue(snapshot.contains("Objects.requireNonNull(jeiCatalog,\"jeiCatalog\")"));
    }

    @Test
    void stagedOperationsPreserveSendOrderAndCountCompletedCalls() throws IOException {
        String source = normalizedSource();
        String staged = substringBetween(
                source,
                "privatebooleansendNext(ServerPlayerplayer)",
                "thrownewIllegalStateException(\"Unknowncontentsyncstage\"+stage);");

        assertOrdered(staged,
                "PacketDistributor.sendToPlayer(player,shared.registry());",
                "ServerPerformanceRecorder.increment(PerformanceCounter.CONTENT_SYNC_PACKETS,1L);",
                "ServerPerformanceRecorder.increment(PerformanceCounter.CONTENT_REGISTRY_BYTES,"
                        + "shared.registryEncodedBytes());",
                "stage=SyncStage.CUTSCENES;",
                "PacketDistributor.sendToPlayer(player,shared.cutscenes());",
                "ServerPerformanceRecorder.increment(PerformanceCounter.CONTENT_SYNC_PACKETS,1L);",
                "stage=SyncStage.MAIL;",
                "PacketDistributor.sendToPlayer(player,shared.mail());",
                "ServerPerformanceRecorder.increment(PerformanceCounter.CONTENT_SYNC_PACKETS,1L);",
                "stage=SyncStage.FESTIVAL;",
                "PacketDistributor.sendToPlayer(player,festival);",
                "ServerPerformanceRecorder.increment(PerformanceCounter.CONTENT_SYNC_PACKETS,1L);",
                "stage=SyncStage.JEI;",
                "JeiCatalogSyncPayloadjeiSnapshot=ServerPerformanceRecorder.measure(",
                "PerformanceTiming.JEI_CATALOG_BUILD,"
                        + "()->JeiCatalogSyncPayload.current(player,shared.jeiCatalog()));",
                "ServerPerformanceRecorder.increment(PerformanceCounter.JEI_CATALOG_ENTRIES,",
                "PacketDistributor.sendToPlayer(player,jeiSnapshot);",
                "ServerPerformanceRecorder.increment(PerformanceCounter.CONTENT_SYNC_PACKETS,1L);");

        assertFalse(datapackSyncHandler().contains("PacketDistributor.sendToPlayer"));
    }

    @Test
    void registryEncodedSizeIsComputedOnceInsideSharedBuildBeforeRecipients() throws IOException {
        String source = normalizedSource();
        String handler = datapackSyncHandler();
        String builder = substringBetween(
                source,
                "privatestaticSharedSnapshotbuildSharedSnapshot(longgeneration)",
                "privaterecordSharedSnapshot(");

        assertEquals(1, occurrences(builder, "registry.estimatedEncodedBytes()"));
        assertEquals(1, occurrences(source, "registry.estimatedEncodedBytes()"));
        assertFalse(handler.contains("estimatedEncodedBytes()"));
        assertOrdered(handler,
                "ClientContentSnapshotCache.Entry<SharedSnapshot>cached=event.getPlayer()==null",
                "SharedSnapshotshared=cached.value();",
                "List<ServerPlayer>recipients=event.getRelevantPlayers().toList();");
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

    @Test
    void serverStopSubscriptionClearsOnlyTheMatchingServer() throws ReflectiveOperationException, IOException {
        Method stopped = ClientContentSyncService.class.getDeclaredMethod("onServerStopped", ServerStoppedEvent.class);
        String source = normalizedSource();
        String handler = source.substring(source.indexOf("publicstaticvoidonServerStopped("));

        assertTrue(Modifier.isStatic(stopped.getModifiers()));
        assertNotNull(stopped.getAnnotation(SubscribeEvent.class));
        assertEquals(1, occurrences(handler, "SHARED_CONTENT.clear(event.getServer())"));
    }

    private static String datapackSyncHandler() throws IOException {
        String source = normalizedSource();
        return substringBetween(
                source,
                "publicstaticvoidonDatapackSync(OnDatapackSyncEventevent)",
                "privatestaticSharedSnapshotbuildSharedSnapshot(longgeneration)");
    }

    private static String normalizedSource() throws IOException {
        return Files.readString(SOURCE).replaceAll("\\s+", "");
    }

    private static String substringBetween(String source, String start, String end) {
        int startIndex = source.indexOf(start);
        int endIndex = source.indexOf(end, startIndex);
        assertTrue(startIndex >= 0 && endIndex > startIndex,
                () -> "missing source section between " + start + " and " + end);
        return source.substring(startIndex, endIndex);
    }

    private static void assertOrdered(String source, String... operations) {
        int cursor = 0;
        for (String operation : operations) {
            int operationIndex = source.indexOf(operation, cursor);
            assertTrue(operationIndex >= cursor, () -> "missing or out-of-order operation: " + operation);
            cursor = operationIndex + operation.length();
        }
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
