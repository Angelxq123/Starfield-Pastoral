package com.stardew.craft.fishing.network;

import com.stardew.craft.StardewCraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import java.util.UUID;

public record BobberStyleStatePayload(UUID actor,int style) implements CustomPacketPayload {
    public static final Type<BobberStyleStatePayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID,"bobber_style_state"));
    public static final StreamCodec<FriendlyByteBuf,BobberStyleStatePayload> CODEC=StreamCodec.of((b,p)->{b.writeUUID(p.actor);b.writeVarInt(p.style);},b->new BobberStyleStatePayload(b.readUUID(),b.readVarInt()));
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    public static void handle(BobberStyleStatePayload p,IPayloadContext c){c.enqueueWork(()->client(p));}
    @net.neoforged.api.distmarker.OnlyIn(net.neoforged.api.distmarker.Dist.CLIENT)
    private static void client(BobberStyleStatePayload p){com.stardew.craft.client.fishing.BobberStyleClient.receive(p);}
}
