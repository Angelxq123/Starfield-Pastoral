package com.stardew.craft.network;

import com.stardew.craft.StardewCraft;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 矿井层数同步包 - 服务端 → 客户端
 */
public record MiningFloorSyncPacket(int currentFloor) implements CustomPacketPayload {
    
    @SuppressWarnings("null")
    public static final CustomPacketPayload.Type<MiningFloorSyncPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "mining_floor_sync"));
    
    @SuppressWarnings("null")
    public static final StreamCodec<ByteBuf, MiningFloorSyncPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.INT,
                    MiningFloorSyncPacket::currentFloor,
                    MiningFloorSyncPacket::new
            );
    
    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
    
    /**
     * 客户端处理（更新 HUD）
     */
    public static void handle(MiningFloorSyncPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            com.stardew.craft.client.hud.MiningFloorHud.setCurrentFloor(packet.currentFloor());
        });
    }
}
