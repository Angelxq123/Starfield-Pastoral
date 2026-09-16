package com.stardew.craft.network.payload;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record OpenBuildingRoutesPayload(int money, int managerPrice, int width, int height, int prefabPrice, net.minecraft.network.chat.Component materials, long revision, ResourceLocation family, java.util.UUID requestId) implements CustomPacketPayload {
    public OpenBuildingRoutesPayload(int money,int managerPrice,int width,int height,int prefabPrice,net.minecraft.network.chat.Component materials,long revision,ResourceLocation family) {
        this(money,managerPrice,width,height,prefabPrice,materials,revision,family,new java.util.UUID(0,0));
    }
    public static final Type<OpenBuildingRoutesPayload> TYPE = new Type<>(ResourceLocation.parse("stardewcraft:open_building_routes_payload"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenBuildingRoutesPayload> STREAM_CODEC = StreamCodec.of(
        (b, p) -> { b.writeInt(p.money); b.writeInt(p.managerPrice); b.writeInt(p.width); b.writeInt(p.height); b.writeInt(p.prefabPrice); net.minecraft.network.chat.ComponentSerialization.STREAM_CODEC.encode(b, p.materials); b.writeLong(p.revision); b.writeResourceLocation(p.family); b.writeUUID(p.requestId); }, b -> new OpenBuildingRoutesPayload(b.readInt(), b.readInt(), b.readInt(), b.readInt(), b.readInt(), net.minecraft.network.chat.ComponentSerialization.STREAM_CODEC.decode(b), b.readLong(), b.readResourceLocation(), b.readUUID()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public static void handle(OpenBuildingRoutesPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> handleClient(payload));
    }
    @net.neoforged.api.distmarker.OnlyIn(net.neoforged.api.distmarker.Dist.CLIENT)
    private static void handleClient(OpenBuildingRoutesPayload payload) { var screen = net.minecraft.client.Minecraft.getInstance().screen;
        if (screen instanceof com.stardew.craft.client.gui.CarpenterMenuScreen catalog) catalog.openChoices(payload); }
}
