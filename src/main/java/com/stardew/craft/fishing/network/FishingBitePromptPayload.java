package com.stardew.craft.fishing.network;

import com.stardew.craft.StardewCraft;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/**
 * Server -> client: a fish has bitten. Show an obvious bite prompt (exclamation) and a short bobber dip.
 */
public record FishingBitePromptPayload(java.util.UUID sessionId, int hookEntityId, int durationTicks) implements CustomPacketPayload {
	@SuppressWarnings("null")
	public static final Type<FishingBitePromptPayload> TYPE = new Type<>(
			ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "fishing_bite_prompt")
	);

	@SuppressWarnings("null")
	public static final StreamCodec<ByteBuf, FishingBitePromptPayload> STREAM_CODEC = StreamCodec.composite(
			net.minecraft.core.UUIDUtil.STREAM_CODEC, FishingBitePromptPayload::sessionId,
			ByteBufCodecs.VAR_INT, FishingBitePromptPayload::hookEntityId,
			ByteBufCodecs.VAR_INT, FishingBitePromptPayload::durationTicks,
			FishingBitePromptPayload::new
	);

	@Override
	public @NotNull Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	public static void handle(FishingBitePromptPayload payload, IPayloadContext context) {
		context.enqueueWork(() -> handleClient(payload));
	}

	@net.neoforged.api.distmarker.OnlyIn(net.neoforged.api.distmarker.Dist.CLIENT)
	private static void handleClient(FishingBitePromptPayload payload) {
		if (!com.stardew.craft.client.fishing.FishingInteractionState.accepts(payload.sessionId())) return;
		net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
		if (mc == null || mc.player == null) {
			return;
		}
		com.stardew.craft.client.fishing.FishingBiteVisuals.startBitePrompt(payload.hookEntityId(), payload.durationTicks());
	}
}
