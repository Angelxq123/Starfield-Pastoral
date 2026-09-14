package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 打开宝箱UI的网络数据包
 * 服务器 -> 客户端
 * 注意：这个数据包只下发元信息。客户端动画结束后再请求服务端真正打开容器。
 */
public record OpenTreasureChestPayload(
		java.util.UUID sessionId,
		long chestId,
		boolean isGolden
) implements CustomPacketPayload {
	
	@SuppressWarnings("null")
	public static final Type<OpenTreasureChestPayload> TYPE = 
			new Type<>(ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "open_treasure_chest"));
	
	@SuppressWarnings("null")
	public static final StreamCodec<RegistryFriendlyByteBuf, OpenTreasureChestPayload> STREAM_CODEC = StreamCodec.composite(
			net.minecraft.core.UUIDUtil.STREAM_CODEC, OpenTreasureChestPayload::sessionId,
			ByteBufCodecs.VAR_LONG,
			OpenTreasureChestPayload::chestId,
			ByteBufCodecs.BOOL,
			OpenTreasureChestPayload::isGolden,
			OpenTreasureChestPayload::new
	);
	
	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
	
	public static void handle(OpenTreasureChestPayload payload, IPayloadContext context) {
		context.enqueueWork(() -> handleClient(payload));
	}

	@net.neoforged.api.distmarker.OnlyIn(net.neoforged.api.distmarker.Dist.CLIENT)
	private static void handleClient(OpenTreasureChestPayload payload) {
		if (!com.stardew.craft.client.fishing.FishingInteractionState.accepts(payload.sessionId())) return;
		net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
		if (mc.player != null && mc.level != null) {
			// 客户端仅缓存 chestId 与外观类型；真正开箱由客户端请求服务端打开。
			com.stardew.craft.client.fishing.FishingCatchVisuals.setPendingTreasure(payload.chestId(), payload.isGolden());
		}
	}
}

