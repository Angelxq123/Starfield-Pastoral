package com.stardew.craft.combat.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Only attack-key intent; clients cannot supply targets, damage, position or a faster cadence. */
public record HeavyHammerInputPayload(boolean held) implements CustomPacketPayload {
    public static final Type<HeavyHammerInputPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("stardewcraft", "heavy_hammer_input"));
    public static final StreamCodec<ByteBuf, HeavyHammerInputPayload> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.BOOL,
            HeavyHammerInputPayload::held, HeavyHammerInputPayload::new);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public static void handle(HeavyHammerInputPayload p, IPayloadContext c) {
        c.enqueueWork(() -> { if(c.player() instanceof ServerPlayer player)
            com.stardew.craft.combat.skill.handler.HeavyHammerSkillHandler.attackIntent(player, p.held); });
    }
}
