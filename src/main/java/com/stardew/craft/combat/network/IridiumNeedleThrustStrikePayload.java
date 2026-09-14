package com.stardew.craft.combat.network;

import com.stardew.craft.StardewCraft;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

public record IridiumNeedleThrustStrikePayload() implements CustomPacketPayload {

    @SuppressWarnings("null")
    public static final Type<IridiumNeedleThrustStrikePayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "iridium_needle_thrust_strike")
    );

    public static final StreamCodec<ByteBuf, IridiumNeedleThrustStrikePayload> STREAM_CODEC =
        StreamCodec.unit(new IridiumNeedleThrustStrikePayload());

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(IridiumNeedleThrustStrikePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> handleClient(payload));
    }

    @net.neoforged.api.distmarker.OnlyIn(net.neoforged.api.distmarker.Dist.CLIENT)
    private static void handleClient(IridiumNeedleThrustStrikePayload payload) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player != null) {
            com.stardew.craft.client.weapon.NeedleBurglarVisuals.strikeConfirmed();
        }
    }
}
