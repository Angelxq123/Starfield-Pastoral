package com.stardew.craft.network;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.api.v1.internal.content.StardewContentRegistry;
import com.stardew.craft.server.performance.PerformanceCounter;
import com.stardew.craft.server.performance.PerformanceTiming;
import com.stardew.craft.server.performance.ServerPerformanceRecorder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
import java.util.Objects;

/** Sends the client-safe datapack snapshot on both login and {@code /reload}. */
@EventBusSubscriber(modid = StardewCraft.MODID)
public final class ClientContentSyncService {
    private static final ClientContentSnapshotCache<MinecraftServer, SharedSnapshot> SHARED_CONTENT =
            new ClientContentSnapshotCache<>();

    private ClientContentSyncService() {
    }

    @SubscribeEvent
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        ServerPerformanceRecorder.measure(PerformanceTiming.CONTENT_SYNC, () -> sync(event));
    }

    private static void sync(OnDatapackSyncEvent event) {
        MinecraftServer server = event.getPlayerList().getServer();
        boolean cacheHit = event.getPlayer() != null && SHARED_CONTENT.contains(server);
        ClientContentSnapshotCache.Entry<SharedSnapshot> cached = event.getPlayer() == null
                ? SHARED_CONTENT.rebuild(server, ClientContentSyncService::buildSharedSnapshot)
                : SHARED_CONTENT.getOrBuild(server, ClientContentSyncService::buildSharedSnapshot);
        ServerPerformanceRecorder.increment(cacheHit
                ? PerformanceCounter.CONTENT_CACHE_HITS
                : PerformanceCounter.CONTENT_CACHE_REBUILDS, 1L);
        SharedSnapshot shared = cached.value();
        if (event.getPlayer() == null) {
            StardewContentRegistry.validateAndLog();
        }
        FestivalAvailabilitySyncPayload festivalSnapshot = FestivalAvailabilitySyncPayload.current();
        List<ServerPlayer> recipients = event.getRelevantPlayers().toList();
        ServerPerformanceRecorder.increment(PerformanceCounter.CONTENT_SYNC_RECIPIENTS, recipients.size());

        for (ServerPlayer player : recipients) {
            PacketDistributor.sendToPlayer(player, shared.registry());
            ServerPerformanceRecorder.increment(PerformanceCounter.CONTENT_SYNC_PACKETS, 1L);
            ServerPerformanceRecorder.increment(PerformanceCounter.CONTENT_REGISTRY_BYTES,
                    shared.registryEncodedBytes());
            PacketDistributor.sendToPlayer(player, shared.mail());
            ServerPerformanceRecorder.increment(PerformanceCounter.CONTENT_SYNC_PACKETS, 1L);
            PacketDistributor.sendToPlayer(player, festivalSnapshot);
            ServerPerformanceRecorder.increment(PerformanceCounter.CONTENT_SYNC_PACKETS, 1L);
            JeiCatalogSyncPayload jeiSnapshot = ServerPerformanceRecorder.measure(
                    PerformanceTiming.JEI_CATALOG_BUILD,
                    () -> JeiCatalogSyncPayload.current(player, shared.jeiCatalog()));
            ServerPerformanceRecorder.increment(PerformanceCounter.JEI_CATALOG_ENTRIES,
                    (long) jeiSnapshot.shops().size()
                            + jeiSnapshot.geodes().size()
                            + jeiSnapshot.fishPonds().size());
            PacketDistributor.sendToPlayer(player, jeiSnapshot);
            ServerPerformanceRecorder.increment(PerformanceCounter.CONTENT_SYNC_PACKETS, 1L);
        }

        StardewCraft.LOGGER.info("[DATA-SYNC] Sent client content snapshot to {} player(s) ({} mail entries)",
                recipients.size(), shared.mail().entries().size());
    }

    private static SharedSnapshot buildSharedSnapshot(long generation) {
        return ServerPerformanceRecorder.measure(PerformanceTiming.CONTENT_SNAPSHOT_BUILD, () -> {
            DataRegistrySyncPayload registry = DataRegistrySyncPayload.current();
            return new SharedSnapshot(
                    registry,
                    registry.estimatedEncodedBytes(),
                    MailIndexSyncPayload.current(),
                    JeiCatalogSyncPayload.currentSharedCatalog());
        });
    }

    private record SharedSnapshot(
            DataRegistrySyncPayload registry,
            int registryEncodedBytes,
            MailIndexSyncPayload mail,
            JeiCatalogSyncPayload.SharedCatalog jeiCatalog
    ) {
        private SharedSnapshot {
            Objects.requireNonNull(registry, "registry");
            if (registryEncodedBytes < 0) {
                throw new IllegalArgumentException("registryEncodedBytes must be nonnegative");
            }
            Objects.requireNonNull(mail, "mail");
            Objects.requireNonNull(jeiCatalog, "jeiCatalog");
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        SHARED_CONTENT.clear(event.getServer());
    }
}
