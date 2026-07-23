package com.stardew.craft.time.settlement;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.network.overnight.OvernightBarrierPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = StardewCraft.MODID)
public final class DailySettlementEvents {
    private DailySettlementEvents() {
    }

    @net.neoforged.bus.api.SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        DailySettlementServices.Services services =
                DailySettlementServices.find(event.getServer());
        if (services != null) {
            services.coordinator().tick();
        }
    }

    @net.neoforged.bus.api.SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        DailySettlementServices.remove(event.getServer());
    }

    public static void onPlayerLogin(ServerPlayer player) {
        DailySettlementServices.Services services =
                DailySettlementServices.find(player.server);
        if (services == null) {
            return;
        }
        services.players().onLogin(player.getUUID());
        int absoluteDay = services.barrier().lockedDay(player.getUUID());
        if (absoluteDay <= 0) {
            return;
        }
        PacketDistributor.sendToPlayer(
                player, new OvernightBarrierPayload(absoluteDay, true));
        DailySettlementBarrier.ReadyResult ready =
                services.barrier().readyResult(player.getUUID(), absoluteDay);
        if (ready != null) {
            PacketDistributor.sendToPlayer(player, ready.payload());
        }
    }

    public static void onPlayerLogout(ServerPlayer player) {
        DailySettlementServices.Services services =
                DailySettlementServices.find(player.server);
        if (services != null) {
            services.players().onLogout(player.getUUID());
        }
    }
}
