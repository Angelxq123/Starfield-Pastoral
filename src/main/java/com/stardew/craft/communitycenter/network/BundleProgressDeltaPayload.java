package com.stardew.craft.communitycenter.network;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.api.v1.internal.communitycenter.StardewCommunityCenterVariantRegistry;
import com.stardew.craft.communitycenter.data.BundleDefinition;
import com.stardew.craft.communitycenter.state.CCStoryFlags;
import com.stardew.craft.communitycenter.state.CommunityCenterSavedData;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Updates one changed bundle while replacing the small shared progress fields. */
@SuppressWarnings("null")
public record BundleProgressDeltaPayload(
        int bundleId,
        boolean[] bundleSlots,
        boolean[] areasComplete,
        Map<Integer, Boolean> bundleRewards,
        boolean canReadJunimoText
) implements CustomPacketPayload {
    public static final Type<BundleProgressDeltaPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "bundle_progress_delta")
    );

    public static final StreamCodec<ByteBuf, BundleProgressDeltaPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public BundleProgressDeltaPayload decode(ByteBuf buf) {
                    int bundleId = ByteBufCodecs.VAR_INT.decode(buf);
                    int slotCount = ByteBufCodecs.VAR_INT.decode(buf);
                    boolean[] slots = new boolean[slotCount];
                    for (int index = 0; index < slotCount; index++) {
                        slots[index] = buf.readBoolean();
                    }

                    boolean[] areas = new boolean[7];
                    for (int index = 0; index < areas.length; index++) {
                        areas[index] = buf.readBoolean();
                    }

                    int rewardCount = ByteBufCodecs.VAR_INT.decode(buf);
                    Map<Integer, Boolean> rewards = new HashMap<>(rewardCount);
                    for (int index = 0; index < rewardCount; index++) {
                        rewards.put(ByteBufCodecs.VAR_INT.decode(buf), true);
                    }
                    boolean canRead = buf.readBoolean();
                    return new BundleProgressDeltaPayload(
                            bundleId, slots, areas, rewards, canRead);
                }

                @Override
                public void encode(ByteBuf buf, BundleProgressDeltaPayload payload) {
                    ByteBufCodecs.VAR_INT.encode(buf, payload.bundleId);
                    ByteBufCodecs.VAR_INT.encode(buf, payload.bundleSlots.length);
                    for (boolean slot : payload.bundleSlots) {
                        buf.writeBoolean(slot);
                    }

                    for (int index = 0; index < 7; index++) {
                        buf.writeBoolean(index < payload.areasComplete.length
                                && payload.areasComplete[index]);
                    }

                    int rewardCount = 0;
                    for (boolean available : payload.bundleRewards.values()) {
                        if (available) {
                            rewardCount++;
                        }
                    }
                    ByteBufCodecs.VAR_INT.encode(buf, rewardCount);
                    for (Map.Entry<Integer, Boolean> reward :
                            payload.bundleRewards.entrySet()) {
                        if (reward.getValue()) {
                            ByteBufCodecs.VAR_INT.encode(buf, reward.getKey());
                        }
                    }
                    buf.writeBoolean(payload.canReadJunimoText);
                }
            };

    public BundleProgressDeltaPayload {
        bundleSlots = bundleSlots.clone();
        areasComplete = areasComplete.clone();
        bundleRewards = Map.copyOf(bundleRewards);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(BundleProgressDeltaPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> BundleClientData.INSTANCE.applyProgressDelta(
                payload.bundleId,
                payload.bundleSlots,
                payload.areasComplete,
                payload.bundleRewards,
                payload.canReadJunimoText));
    }

    public static void send(ServerPlayer player, int bundleId) {
        UUID playerId = player.getUUID();
        Collection<BundleDefinition> definitions =
                StardewCommunityCenterVariantRegistry.all(playerId);
        BundleDefinitionSyncPayload.sendIfChanged(player, definitions);

        CommunityCenterSavedData data = CommunityCenterSavedData.get(player.serverLevel());
        boolean[] slots = data.getSlots(playerId, bundleId).clone();
        boolean[] areas = new boolean[7];
        for (int areaId = 0; areaId < areas.length; areaId++) {
            areas[areaId] = data.isAreaComplete(playerId, areaId);
        }

        Map<Integer, Boolean> rewards = new HashMap<>();
        for (BundleDefinition definition : definitions) {
            if (data.isRewardAvailable(playerId, definition.bundleId())) {
                rewards.put(definition.bundleId(), true);
            }
        }

        PacketDistributor.sendToPlayer(player, new BundleProgressDeltaPayload(
                bundleId,
                slots,
                areas,
                rewards,
                CCStoryFlags.canReadJunimoText(player)));
    }
}
