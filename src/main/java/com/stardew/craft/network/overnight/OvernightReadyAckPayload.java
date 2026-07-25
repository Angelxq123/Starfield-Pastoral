package com.stardew.craft.network.overnight;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.time.settlement.DailySettlementServices;
import com.stardew.craft.time.settlement.DailySettlementBarrier;
import com.stardew.craft.time.settlement.DailySettlementEvents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record OvernightReadyAckPayload(int absoluteDay) implements CustomPacketPayload {
    public static final Type<OvernightReadyAckPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "overnight_ready_ack"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OvernightReadyAckPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, OvernightReadyAckPayload::absoluteDay,
                    OvernightReadyAckPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(OvernightReadyAckPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            DailySettlementServices.Services services =
                    DailySettlementServices.getForPlayer(player);
            DailySettlementBarrier.ReadyResult ready = services.barrier().readyResult(
                    player.getUUID(), payload.absoluteDay());
            if (ready == null || !ready.canAcknowledge(
                    services.players().hasCompletedReady(
                            player.getUUID(), payload.absoluteDay()))) {
                return;
            }
            if (services.accessGuard().acknowledge(
                    player.getUUID(), payload.absoluteDay())) {
                if (ready.personalSettlement()) {
                    services.players().acknowledgeReady(
                            player.getUUID(), payload.absoluteDay());
                }
                DailySettlementEvents.onReadyAcknowledged(player);
            }
        });
    }
}
