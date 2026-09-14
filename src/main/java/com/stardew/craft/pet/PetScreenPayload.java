package com.stardew.craft.pet;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record PetScreenPayload(CompoundTag data) implements CustomPacketPayload {
    public static final Type<PetScreenPayload> TYPE = new Type<>(ResourceLocation.parse("stardewcraft:pet_screen"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PetScreenPayload> CODEC = StreamCodec.of((b, p) -> b.writeNbt(p.data), b -> new PetScreenPayload(b.readNbt()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public static void handle(PetScreenPayload payload, IPayloadContext context) { context.enqueueWork(() -> client(payload.data)); }
    @net.neoforged.api.distmarker.OnlyIn(net.neoforged.api.distmarker.Dist.CLIENT)
    private static void client(CompoundTag data) { com.stardew.craft.client.pet.PetScreen.receive(data); }
}
