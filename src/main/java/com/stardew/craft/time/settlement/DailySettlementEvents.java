package com.stardew.craft.time.settlement;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.network.overnight.OvernightBarrierPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Optional;
import java.util.UUID;

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
            DailySettlementBarrier.ReadyResult recovered =
                    PlayerDailySettlementService.recoverPending(player).orElse(null);
            if (recovered != null) {
                PacketDistributor.sendToPlayer(
                        player, new OvernightBarrierPayload(recovered.absoluteDay(), true));
                PacketDistributor.sendToPlayer(player, recovered.payload());
                com.stardew.craft.cutscene.server.WakeUpEventScheduler
                        .enqueueAtNightSettlement(player);
            }
            return;
        }
        boolean coordinatorOwns = services.coordinator().context()
                .map(context -> context.playerIds().contains(player.getUUID()))
                .orElse(false);
        resumePlayerSettlement(
                services.players(), services.barrier(), player.getUUID(), coordinatorOwns);
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

    static Optional<DailySettlementBarrier.ReadyResult> resumePlayerSettlement(
            PlayerDailySettlementService players,
            DailySettlementBarrier barrier,
            UUID playerId,
            boolean coordinatorOwns) {
        if (coordinatorOwns) {
            return Optional.empty();
        }
        return players.onLogin(playerId, barrier);
    }
}
