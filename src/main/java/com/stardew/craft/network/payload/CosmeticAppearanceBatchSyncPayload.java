package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.client.ClientPlayerDataCache;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@SuppressWarnings("null")
public record CosmeticAppearanceBatchSyncPayload(List<Appearance> appearances)
        implements CustomPacketPayload {
    public static final Type<CosmeticAppearanceBatchSyncPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(
                    StardewCraft.MODID, "cosmetic_appearance_batch_sync"));

    public static final StreamCodec<FriendlyByteBuf, CosmeticAppearanceBatchSyncPayload>
            STREAM_CODEC = StreamCodec.of(
                    (buf, payload) -> {
                        buf.writeVarInt(payload.appearances.size());
                        for (Appearance appearance : payload.appearances) {
                            buf.writeUUID(appearance.playerId);
                            buf.writeUtf(appearance.hat);
                            buf.writeUtf(appearance.shirt);
                            buf.writeUtf(appearance.pants);
                        }
                    },
                    buf -> {
                        int size = buf.readVarInt();
                        List<Appearance> appearances = new ArrayList<>(size);
                        for (int index = 0; index < size; index++) {
                            appearances.add(new Appearance(
                                    buf.readUUID(),
                                    buf.readUtf(),
                                    buf.readUtf(),
                                    buf.readUtf()));
                        }
                        return new CosmeticAppearanceBatchSyncPayload(appearances);
                    });

    public CosmeticAppearanceBatchSyncPayload {
        appearances = List.copyOf(appearances);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(
            CosmeticAppearanceBatchSyncPayload payload,
            IPayloadContext context
    ) {
        context.enqueueWork(() -> {
            for (Appearance appearance : payload.appearances) {
                ClientPlayerDataCache.setCosmeticAppearance(
                        appearance.playerId,
                        appearance.hat,
                        appearance.shirt,
                        appearance.pants);
            }
        });
    }

    public record Appearance(UUID playerId, String hat, String shirt, String pants) {
    }
}
