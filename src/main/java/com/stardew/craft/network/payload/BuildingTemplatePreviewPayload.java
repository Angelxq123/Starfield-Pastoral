package com.stardew.craft.network.payload;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record BuildingTemplatePreviewPayload(CompoundTag template) implements CustomPacketPayload {
    public static final Type<BuildingTemplatePreviewPayload> TYPE = new Type<>(ResourceLocation.parse("stardewcraft:building_template_preview"));
    public static final StreamCodec<FriendlyByteBuf, BuildingTemplatePreviewPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> buffer.writeNbt(payload.template), buffer -> new BuildingTemplatePreviewPayload(buffer.readNbt()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public static void handle(BuildingTemplatePreviewPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> client(payload));
    }
    @net.neoforged.api.distmarker.OnlyIn(net.neoforged.api.distmarker.Dist.CLIENT)
    private static void client(BuildingTemplatePreviewPayload payload) { com.stardew.craft.client.building.BuildingTemplatePreview.load(payload.template); }
}
