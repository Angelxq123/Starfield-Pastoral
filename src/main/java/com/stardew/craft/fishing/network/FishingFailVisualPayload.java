package com.stardew.craft.fishing.network;

import com.stardew.craft.StardewCraft;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/** Server -> client: show a short "failed/escaped" visual. */
public record FishingFailVisualPayload(java.util.UUID sessionId) implements CustomPacketPayload {
	@SuppressWarnings("null")
	public static final Type<FishingFailVisualPayload> TYPE = new Type<>(
			ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "fishing_fail_visual")
	);
	public static final StreamCodec<ByteBuf, FishingFailVisualPayload> STREAM_CODEC = StreamCodec.composite(net.minecraft.core.UUIDUtil.STREAM_CODEC, FishingFailVisualPayload::sessionId, FishingFailVisualPayload::new);

	@Override
	public @NotNull Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	public static void handle(FishingFailVisualPayload payload, IPayloadContext context) {
		context.enqueueWork(() -> handleClient(payload));
	}

	@net.neoforged.api.distmarker.OnlyIn(net.neoforged.api.distmarker.Dist.CLIENT)
	private static void handleClient(FishingFailVisualPayload payload) {
		if (!com.stardew.craft.client.fishing.FishingInteractionState.accepts(payload.sessionId())) return;
		net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
		if (mc.player == null) {
			return;
		}
		com.stardew.craft.client.fishing.FishingCatchVisuals.startFail();
	}
}
