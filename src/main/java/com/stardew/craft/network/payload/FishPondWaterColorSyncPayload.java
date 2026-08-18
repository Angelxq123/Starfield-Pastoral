package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.client.fishpond.ClientFishPondWaterColorCache;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public record FishPondWaterColorSyncPayload(
        String dimensionId,
        int chunkX,
        int chunkZ,
        boolean replaceChunk,
        Map<BlockPos, Integer> colors,
        List<BlockPos> removedCells
)
        implements CustomPacketPayload {

    public static final Type<FishPondWaterColorSyncPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "fish_pond_water_color_sync")
    );

    public static final StreamCodec<ByteBuf, FishPondWaterColorSyncPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public FishPondWaterColorSyncPayload decode(ByteBuf buf) {
                    String dimensionId = ByteBufCodecs.STRING_UTF8.decode(buf);
                    int chunkX = ByteBufCodecs.INT.decode(buf);
                    int chunkZ = ByteBufCodecs.INT.decode(buf);
                    boolean replaceChunk = buf.readBoolean();
                    int colorCount = ByteBufCodecs.VAR_INT.decode(buf);
                    Map<BlockPos, Integer> colors = new LinkedHashMap<>(colorCount);
                    for (int index = 0; index < colorCount; index++) {
                        colors.put(BlockPos.STREAM_CODEC.decode(buf), ByteBufCodecs.INT.decode(buf));
                    }
                    int removedCount = ByteBufCodecs.VAR_INT.decode(buf);
                    List<BlockPos> removedCells = new ArrayList<>(removedCount);
                    for (int index = 0; index < removedCount; index++) {
                        removedCells.add(BlockPos.STREAM_CODEC.decode(buf));
                    }
                    return new FishPondWaterColorSyncPayload(
                            dimensionId, chunkX, chunkZ, replaceChunk, colors, removedCells);
                }

                @Override
                public void encode(ByteBuf buf, FishPondWaterColorSyncPayload payload) {
                    ByteBufCodecs.STRING_UTF8.encode(buf, payload.dimensionId);
                    ByteBufCodecs.INT.encode(buf, payload.chunkX);
                    ByteBufCodecs.INT.encode(buf, payload.chunkZ);
                    buf.writeBoolean(payload.replaceChunk);
                    ByteBufCodecs.VAR_INT.encode(buf, payload.colors.size());
                    for (Map.Entry<BlockPos, Integer> entry : payload.colors.entrySet()) {
                        BlockPos.STREAM_CODEC.encode(buf, entry.getKey());
                        ByteBufCodecs.INT.encode(buf, entry.getValue());
                    }
                    ByteBufCodecs.VAR_INT.encode(buf, payload.removedCells.size());
                    for (BlockPos pos : payload.removedCells) {
                        BlockPos.STREAM_CODEC.encode(buf, pos);
                    }
                }
            };

    public FishPondWaterColorSyncPayload {
        colors = Map.copyOf(colors);
        removedCells = List.copyOf(removedCells);
    }

    public static FishPondWaterColorSyncPayload chunkSnapshot(
            String dimensionId,
            int chunkX,
            int chunkZ,
            Map<BlockPos, Integer> colors
    ) {
        return new FishPondWaterColorSyncPayload(
                dimensionId, chunkX, chunkZ, true, colors, List.of());
    }

    public static FishPondWaterColorSyncPayload delta(
            String dimensionId,
            int chunkX,
            int chunkZ,
            Map<BlockPos, Integer> colors,
            List<BlockPos> removedCells
    ) {
        return new FishPondWaterColorSyncPayload(
                dimensionId, chunkX, chunkZ, false, colors, removedCells);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(FishPondWaterColorSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (payload.replaceChunk) {
                ClientFishPondWaterColorCache.replaceChunk(
                        payload.dimensionId, payload.chunkX, payload.chunkZ, payload.colors);
            } else {
                ClientFishPondWaterColorCache.applyDelta(
                        payload.dimensionId, payload.colors, payload.removedCells);
            }
        });
    }
}
