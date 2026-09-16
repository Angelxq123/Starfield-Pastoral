package com.stardew.craft.fishing.network;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.fishing.server.FishingSessionManager;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/** A use identity starts before vanilla charging and survives through reward presentation. */
public record FishingUsePayload(UUID useId, boolean begin) implements CustomPacketPayload {
	public static final Type<FishingUsePayload> TYPE = new Type<>(
			ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "fishing_use"));
	public static final StreamCodec<ByteBuf, FishingUsePayload> STREAM_CODEC = StreamCodec.composite(
			UUIDUtil.STREAM_CODEC, FishingUsePayload::useId,
			ByteBufCodecs.BOOL, FishingUsePayload::begin, FishingUsePayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() { return TYPE; }

	public static void handle(FishingUsePayload payload, IPayloadContext context) {
		context.enqueueWork(() -> {
			if (context.player() instanceof ServerPlayer player) {
				var manager = FishingSessionManager.get(player.server);
				if (payload.begin()) manager.prepareUse(player, payload.useId());
				else if (payload.useId().equals(manager.useId(player))) manager.cancel(player);
			} else {
				handleClient(payload);
			}
		});
	}

	@net.neoforged.api.distmarker.OnlyIn(net.neoforged.api.distmarker.Dist.CLIENT)
	private static void handleClient(FishingUsePayload payload) {
		if (!payload.begin()) com.stardew.craft.client.fishing.FishingInteractionState.cancelFromServer(payload.useId());
	}
}
