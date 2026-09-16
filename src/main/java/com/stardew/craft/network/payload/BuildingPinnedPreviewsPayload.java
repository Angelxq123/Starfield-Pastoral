package com.stardew.craft.network.payload;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record BuildingPinnedPreviewsPayload(CompoundTag data) implements CustomPacketPayload {
    public static final Type<BuildingPinnedPreviewsPayload> TYPE=new Type<>(ResourceLocation.parse("stardewcraft:building_pinned_previews"));
    public static final StreamCodec<FriendlyByteBuf,BuildingPinnedPreviewsPayload> CODEC=StreamCodec.of((b,p)->b.writeNbt(p.data),b->new BuildingPinnedPreviewsPayload(b.readNbt()));
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    public static void handle(BuildingPinnedPreviewsPayload p,IPayloadContext context){context.enqueueWork(()->client(p));}
    @net.neoforged.api.distmarker.OnlyIn(net.neoforged.api.distmarker.Dist.CLIENT)
    private static void client(BuildingPinnedPreviewsPayload p){com.stardew.craft.client.building.BuildingPlacementPreview.acceptPins(p.data);}
}
