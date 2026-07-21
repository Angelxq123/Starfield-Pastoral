package com.stardew.craft.network;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.server.performance.PerformanceCounter;
import com.stardew.craft.server.performance.PerformanceTiming;
import com.stardew.craft.server.performance.ServerPerformanceRecorder;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/** Sends the client-safe datapack snapshot on both login and {@code /reload}. */
@EventBusSubscriber(modid = StardewCraft.MODID)
public final class ClientContentSyncService {
    private ClientContentSyncService() {
    }

    @SubscribeEvent
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        DataRegistrySyncPayload registrySnapshot = ServerPerformanceRecorder.measure(
                PerformanceTiming.CONTENT_SNAPSHOT_BUILD, DataRegistrySyncPayload::current);
        MailIndexSyncPayload mailSnapshot = MailIndexSyncPayload.current();
        FestivalAvailabilitySyncPayload festivalSnapshot = FestivalAvailabilitySyncPayload.current();
        List<ServerPlayer> recipients = event.getRelevantPlayers().toList();

        ServerPerformanceRecorder.increment(PerformanceCounter.CONTENT_SYNC_RECIPIENTS, recipients.size());
        ServerPerformanceRecorder.increment(PerformanceCounter.CONTENT_SYNC_PACKETS, recipients.size() * 4L);
        ServerPerformanceRecorder.increment(PerformanceCounter.CONTENT_REGISTRY_BYTES,
                recipients.size() * (long) registrySnapshot.estimatedEncodedBytes());

        for (ServerPlayer player : recipients) {
            JeiCatalogSyncPayload jeiSnapshot = ServerPerformanceRecorder.measure(
                    PerformanceTiming.JEI_CATALOG_BUILD, () -> JeiCatalogSyncPayload.current(player));
            ServerPerformanceRecorder.increment(PerformanceCounter.JEI_CATALOG_ENTRIES,
                    (long) jeiSnapshot.shops().size()
                            + jeiSnapshot.geodes().size()
                            + jeiSnapshot.fishPonds().size());
            PacketDistributor.sendToPlayer(player, registrySnapshot);
            PacketDistributor.sendToPlayer(player, mailSnapshot);
            PacketDistributor.sendToPlayer(player, festivalSnapshot);
            PacketDistributor.sendToPlayer(player, jeiSnapshot);
        }

        StardewCraft.LOGGER.info("[DATA-SYNC] Sent client content snapshot to {} player(s) ({} mail entries)",
                recipients.size(), mailSnapshot.entries().size());
    }
}
