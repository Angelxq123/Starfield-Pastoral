package com.stardew.craft.event;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.museum.MuseumDonationData;
import com.stardew.craft.network.MuseumDonationSyncPacket;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/**
 * Sync museum donation data to clients.
 */
@EventBusSubscriber(modid = StardewCraft.MODID)
public class MuseumDonationSyncEvents {

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            syncToPlayer(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            syncToPlayer(player);
        }
    }

    private static void syncToPlayer(ServerPlayer player) {
        MuseumDonationData data = MuseumDonationData.get(player.serverLevel());
        PacketDistributor.sendToPlayer(player,
                new MuseumDonationSyncPacket(List.copyOf(data.getDonatedItems(player.getUUID()))));
        com.stardew.craft.block.utility.MuseumExhibitStandBlock.ensureAndSyncStands(player.serverLevel(), data, player);
    }

    @EventBusSubscriber(modid = StardewCraft.MODID, value = Dist.CLIENT)
    public static class ClientEvents {
        @SubscribeEvent
        public static void onClientDisconnect(PlayerEvent.PlayerLoggedOutEvent event) {
            com.stardew.craft.client.ClientMuseumDonationCache.clear();
            com.stardew.craft.client.ClientMuseumStandCache.clear();
        }
    }
}
