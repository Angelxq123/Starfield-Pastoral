package com.stardew.craft.network;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.api.v1.internal.content.StardewContentRegistry;
import com.stardew.craft.cutscene.network.SyncEventRegistryPayload;
import com.stardew.craft.server.performance.PerformanceCounter;
import com.stardew.craft.server.performance.PerformanceTiming;
import com.stardew.craft.server.performance.ServerPerformanceRecorder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayDeque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;

/** Sends the client-safe datapack snapshot on both login and {@code /reload}. */
@EventBusSubscriber(modid = StardewCraft.MODID)
public final class ClientContentSyncService {
    private static final int MAX_PLAYERS_PER_TICK = 4;
    private static final ClientContentSnapshotCache<MinecraftServer, SharedSnapshot> SHARED_CONTENT =
            new ClientContentSnapshotCache<>();
    private static final Queue<PendingSync> PENDING_SYNCS = new ArrayDeque<>();

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

        Set<UUID> recipientIds = new LinkedHashSet<>();
        for (ServerPlayer player : recipients) {
            recipientIds.add(player.getUUID());
        }
        PENDING_SYNCS.removeIf(pending -> pending.server == server
                && recipientIds.contains(pending.playerId));
        for (ServerPlayer player : recipients) {
            PENDING_SYNCS.add(new PendingSync(
                    server, player.getUUID(), cached.generation(), shared, festivalSnapshot));
        }

        StardewCraft.LOGGER.info("[DATA-SYNC] Queued client content snapshot for {} player(s) ({} mail entries)",
                recipients.size(), shared.mail().entries().size());
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        int jobsThisTick = Math.min(MAX_PLAYERS_PER_TICK, PENDING_SYNCS.size());
        for (int index = 0; index < jobsThisTick; index++) {
            PendingSync pending = PENDING_SYNCS.poll();
            if (pending == null) {
                return;
            }
            if (pending.server != event.getServer()) {
                PENDING_SYNCS.add(pending);
                continue;
            }
            ServerPlayer player = pending.server.getPlayerList().getPlayer(pending.playerId);
            if (player == null) {
                continue;
            }
            try {
                if (pending.sendNext(player)) {
                    PENDING_SYNCS.add(pending);
                }
            } catch (RuntimeException exception) {
                StardewCraft.LOGGER.error(
                        "[DATA-SYNC] Failed staged content sync for {} at generation {}",
                        player.getGameProfile().getName(), pending.generation, exception);
            }
        }
    }

    private static SharedSnapshot buildSharedSnapshot(long generation) {
        return ServerPerformanceRecorder.measure(PerformanceTiming.CONTENT_SNAPSHOT_BUILD, () -> {
            DataRegistrySyncPayload registry = DataRegistrySyncPayload.current();
            return new SharedSnapshot(
                    registry,
                    registry.estimatedEncodedBytes(),
                    SyncEventRegistryPayload.current(),
                    MailIndexSyncPayload.current(),
                    JeiCatalogSyncPayload.currentSharedCatalog());
        });
    }

    private record SharedSnapshot(
            DataRegistrySyncPayload registry,
            int registryEncodedBytes,
            SyncEventRegistryPayload cutscenes,
            MailIndexSyncPayload mail,
            JeiCatalogSyncPayload.SharedCatalog jeiCatalog
    ) {
        private SharedSnapshot {
            Objects.requireNonNull(registry, "registry");
            if (registryEncodedBytes < 0) {
                throw new IllegalArgumentException("registryEncodedBytes must be nonnegative");
            }
            Objects.requireNonNull(cutscenes, "cutscenes");
            Objects.requireNonNull(mail, "mail");
            Objects.requireNonNull(jeiCatalog, "jeiCatalog");
        }
    }

    private enum SyncStage {
        REGISTRY,
        CUTSCENES,
        MAIL,
        FESTIVAL,
        JEI
    }

    private static final class PendingSync {
        private final MinecraftServer server;
        private final UUID playerId;
        private final long generation;
        private final SharedSnapshot shared;
        private final FestivalAvailabilitySyncPayload festival;
        private SyncStage stage = SyncStage.REGISTRY;

        private PendingSync(
                MinecraftServer server,
                UUID playerId,
                long generation,
                SharedSnapshot shared,
                FestivalAvailabilitySyncPayload festival
        ) {
            this.server = Objects.requireNonNull(server, "server");
            this.playerId = Objects.requireNonNull(playerId, "playerId");
            this.generation = generation;
            this.shared = Objects.requireNonNull(shared, "shared");
            this.festival = Objects.requireNonNull(festival, "festival");
        }

        private boolean sendNext(ServerPlayer player) {
            switch (stage) {
                case REGISTRY -> {
                    PacketDistributor.sendToPlayer(player, shared.registry());
                    ServerPerformanceRecorder.increment(PerformanceCounter.CONTENT_SYNC_PACKETS, 1L);
                    ServerPerformanceRecorder.increment(
                            PerformanceCounter.CONTENT_REGISTRY_BYTES,
                            shared.registryEncodedBytes());
                    stage = SyncStage.CUTSCENES;
                    return true;
                }
                case CUTSCENES -> {
                    PacketDistributor.sendToPlayer(player, shared.cutscenes());
                    ServerPerformanceRecorder.increment(PerformanceCounter.CONTENT_SYNC_PACKETS, 1L);
                    stage = SyncStage.MAIL;
                    return true;
                }
                case MAIL -> {
                    PacketDistributor.sendToPlayer(player, shared.mail());
                    ServerPerformanceRecorder.increment(PerformanceCounter.CONTENT_SYNC_PACKETS, 1L);
                    stage = SyncStage.FESTIVAL;
                    return true;
                }
                case FESTIVAL -> {
                    PacketDistributor.sendToPlayer(player, festival);
                    ServerPerformanceRecorder.increment(PerformanceCounter.CONTENT_SYNC_PACKETS, 1L);
                    stage = SyncStage.JEI;
                    return true;
                }
                case JEI -> {
                    JeiCatalogSyncPayload jeiSnapshot = ServerPerformanceRecorder.measure(
                            PerformanceTiming.JEI_CATALOG_BUILD,
                            () -> JeiCatalogSyncPayload.current(player, shared.jeiCatalog()));
                    ServerPerformanceRecorder.increment(PerformanceCounter.JEI_CATALOG_ENTRIES,
                            (long) jeiSnapshot.shops().size()
                                    + jeiSnapshot.geodes().size()
                                    + jeiSnapshot.fishPonds().size());
                    PacketDistributor.sendToPlayer(player, jeiSnapshot);
                    ServerPerformanceRecorder.increment(PerformanceCounter.CONTENT_SYNC_PACKETS, 1L);
                    return false;
                }
            }
            throw new IllegalStateException("Unknown content sync stage " + stage);
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        PENDING_SYNCS.removeIf(pending -> pending.server == event.getServer());
        SHARED_CONTENT.clear(event.getServer());
    }
}
