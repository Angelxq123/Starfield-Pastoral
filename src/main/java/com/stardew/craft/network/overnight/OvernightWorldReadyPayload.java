package com.stardew.craft.network.overnight;

import com.stardew.craft.StardewCraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server-to-client signal that all world-side overnight work is committed.
 *
 * <p>The player settlement payload may arrive earlier so the client can prepare
 * the result screen while crops, trees and other world work continue. This
 * signal is the final ACK gate; it does not advance the date by itself.</p>
 */
public record OvernightWorldReadyPayload(int absoluteDay) implements CustomPacketPayload {
    public static final Type<OvernightWorldReadyPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "overnight_world_ready"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OvernightWorldReadyPayload>
            STREAM_CODEC = StreamCodec.composite(
                    ByteBufCodecs.VAR_INT,
                    OvernightWorldReadyPayload::absoluteDay,
                    OvernightWorldReadyPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(
            OvernightWorldReadyPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientOvernightHandler.receiveWorldReady(payload));
    }
}
