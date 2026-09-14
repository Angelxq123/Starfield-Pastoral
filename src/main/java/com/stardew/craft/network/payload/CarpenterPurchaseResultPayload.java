package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server → Client: result of a carpenter purchase attempt.
 */
@SuppressWarnings("null")
public record CarpenterPurchaseResultPayload(
    boolean success,
    int     newMoney,
    String  resultItemId,
    int     blueprintIndex,
    java.util.UUID requestId
) implements CustomPacketPayload {

    public CarpenterPurchaseResultPayload(boolean success, int newMoney, String resultItemId, int blueprintIndex) {
        this(success, newMoney, resultItemId, blueprintIndex, new java.util.UUID(0, 0));
    }

    public static final Type<CarpenterPurchaseResultPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "carpenter_purchase_result"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CarpenterPurchaseResultPayload> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.BOOL,        CarpenterPurchaseResultPayload::success,
            ByteBufCodecs.INT,         CarpenterPurchaseResultPayload::newMoney,
            ByteBufCodecs.STRING_UTF8, CarpenterPurchaseResultPayload::resultItemId,
            ByteBufCodecs.INT,         CarpenterPurchaseResultPayload::blueprintIndex,
            net.minecraft.core.UUIDUtil.STREAM_CODEC, CarpenterPurchaseResultPayload::requestId,
            CarpenterPurchaseResultPayload::new
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(CarpenterPurchaseResultPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> handleClient(payload));
    }

    @net.neoforged.api.distmarker.OnlyIn(net.neoforged.api.distmarker.Dist.CLIENT)
    private static void handleClient(CarpenterPurchaseResultPayload payload) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.screen instanceof com.stardew.craft.client.building.BuildingRoutesScreen routes) routes.result(payload);
        else if (mc.screen instanceof com.stardew.craft.client.gui.CarpenterMenuScreen screen) {
            screen.onPurchaseResult(payload);
        }
    }
}
