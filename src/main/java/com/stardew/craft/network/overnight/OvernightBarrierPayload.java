package com.stardew.craft.network.overnight;

import com.stardew.craft.StardewCraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record OvernightBarrierPayload(int absoluteDay, boolean locked) implements CustomPacketPayload {
    public static final Type<OvernightBarrierPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "overnight_barrier"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OvernightBarrierPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, OvernightBarrierPayload::absoluteDay,
                    ByteBufCodecs.BOOL, OvernightBarrierPayload::locked,
                    OvernightBarrierPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(OvernightBarrierPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> handleClient(payload));
    }

    @net.neoforged.api.distmarker.OnlyIn(net.neoforged.api.distmarker.Dist.CLIENT)
    private static void handleClient(OvernightBarrierPayload payload) {
        ClientOvernightHandler.receiveBarrierState(payload);
    }
}
