package com.stardew.craft.communitycenter.network;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.communitycenter.data.BundleDefinition;
import com.stardew.craft.communitycenter.state.CCStoryFlags;
import com.stardew.craft.communitycenter.state.CommunityCenterSavedData;
import com.stardew.craft.api.v1.internal.communitycenter.StardewCommunityCenterVariantRegistry;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.HashMap;
import java.util.Map;

/**
 * S→C: Full sync of player-specific Community Center progress.
 * <p>
 * Wire format:
 *   1) Progress: bundleCount, then for each: bundleId, slotCount, slotBits
 *   2) 7 area completion booleans
 *   3) Rewards: rewardCount, then bundleId for each claimable
 *   4) canReadJunimoText boolean
 */
@SuppressWarnings("null")
public record BundleSyncPayload(
        Map<Integer, boolean[]> bundleSlots,
        boolean[] areasComplete,
        Map<Integer, Boolean> bundleRewards,
        boolean canReadJunimoText
) implements CustomPacketPayload {

    public static final Type<BundleSyncPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "bundle_sync")
    );

    public static final StreamCodec<ByteBuf, BundleSyncPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public BundleSyncPayload decode(ByteBuf buf) {
            // 1) Progress
            int bundleCount = ByteBufCodecs.VAR_INT.decode(buf);
            Map<Integer, boolean[]> slots = new HashMap<>();
            for (int i = 0; i < bundleCount; i++) {
                int bundleId = ByteBufCodecs.VAR_INT.decode(buf);
                int slotCount = ByteBufCodecs.VAR_INT.decode(buf);
                boolean[] slotArr = new boolean[slotCount];
                for (int s = 0; s < slotCount; s++) {
                    slotArr[s] = buf.readBoolean();
                }
                slots.put(bundleId, slotArr);
            }

            // 2) Area completion
            boolean[] areas = new boolean[7];
            for (int i = 0; i < 7; i++) {
                areas[i] = buf.readBoolean();
            }

            // 3) Rewards
            int rewardCount = ByteBufCodecs.VAR_INT.decode(buf);
            Map<Integer, Boolean> rewards = new HashMap<>();
            for (int i = 0; i < rewardCount; i++) {
                int bundleId = ByteBufCodecs.VAR_INT.decode(buf);
                rewards.put(bundleId, true);
            }

            // 4) canRead
            boolean canRead = buf.readBoolean();
            return new BundleSyncPayload(slots, areas, rewards, canRead);
        }

        @Override
        public void encode(ByteBuf buf, BundleSyncPayload payload) {
            // 1) Progress
            ByteBufCodecs.VAR_INT.encode(buf, payload.bundleSlots.size());
            for (var entry : payload.bundleSlots.entrySet()) {
                ByteBufCodecs.VAR_INT.encode(buf, entry.getKey());
                boolean[] slots = entry.getValue();
                ByteBufCodecs.VAR_INT.encode(buf, slots.length);
                for (boolean slot : slots) {
                    buf.writeBoolean(slot);
                }
            }

            // 2) Area completion
            for (int i = 0; i < 7; i++) {
                buf.writeBoolean(i < payload.areasComplete.length && payload.areasComplete[i]);
            }

            // 3) Rewards
            int rewardCount = 0;
            for (var entry : payload.bundleRewards.entrySet()) {
                if (entry.getValue()) rewardCount++;
            }
            ByteBufCodecs.VAR_INT.encode(buf, rewardCount);
            for (var entry : payload.bundleRewards.entrySet()) {
                if (entry.getValue()) {
                    ByteBufCodecs.VAR_INT.encode(buf, entry.getKey());
                }
            }

            // 4) canRead
            buf.writeBoolean(payload.canReadJunimoText);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * Client-side handler: store the synced data in a client-side cache.
     */
    public static void handle(BundleSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            BundleClientData.INSTANCE.update(payload.bundleSlots, payload.areasComplete, payload.bundleRewards, payload.canReadJunimoText);
        });
    }

    /**
     * Send full sync from server to the given player.
     */
    public static void sendFullSync(ServerPlayer player) {
        CommunityCenterSavedData data = CommunityCenterSavedData.get();
        java.util.UUID uuid = player.getUUID();
        java.util.Collection<BundleDefinition> resolvedDefinitions =
                StardewCommunityCenterVariantRegistry.all(uuid);
        BundleDefinitionSyncPayload.sendIfChanged(player, resolvedDefinitions);

        Map<Integer, boolean[]> allSlots = data.getBundleSlotsView(uuid);
        Map<Integer, boolean[]> slots = new HashMap<>();
        for (BundleDefinition def : resolvedDefinitions) {
            boolean[] bundleSlots = allSlots.get(def.bundleId());
            if (bundleSlots != null) {
                slots.put(def.bundleId(), bundleSlots.clone());
            }
        }

        boolean[] areas = new boolean[7];
        for (int i = 0; i < 7; i++) {
            areas[i] = data.isAreaComplete(uuid, i);
        }

        Map<Integer, Boolean> rewards = new HashMap<>();
        for (BundleDefinition def : resolvedDefinitions) {
            if (data.isRewardAvailable(uuid, def.bundleId())) {
                rewards.put(def.bundleId(), true);
            }
        }

        boolean canRead = CCStoryFlags.canReadJunimoText(player);

        // 获取该玩家的 CC 原点
        net.minecraft.core.BlockPos ccOrigin = com.stardew.craft.interior.InteriorSubspaceManager.CC_ORIGIN;
        if (player.level() instanceof net.minecraft.server.level.ServerLevel sl) {
            ccOrigin = com.stardew.craft.interior.PlayerInteriorAllocator.get(sl).getCCOrigin(uuid);
        }

        PacketDistributor.sendToPlayer(player, new BundleSyncPayload(slots, areas, rewards, canRead));

        // 同步 CC 原点到客户端（通过 BundleClientData）
        final net.minecraft.core.BlockPos origin = ccOrigin;
        // 用一个额外的轻量 payload 或直接在 handle 中设置
        // 这里直接通过在 BundleSyncPayload handle 后设置 — 需要额外的同步机制
        // 暂时方案：让 sendFullSync 后发送一个 CcOriginPayload
        // TODO: 可以在 BundleSyncPayload record 添加 ccOrigin 字段来优化
        PacketDistributor.sendToPlayer(player,
            new com.stardew.craft.communitycenter.network.CcOriginPayload(origin));
    }
}
