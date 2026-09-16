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
 * Server -> client: the player reacted to the bite. Play the "Hooked!" animation before opening the minigame.
 */
public record FishingHookedAnimPayload(java.util.UUID sessionId, int durationTicks) implements CustomPacketPayload {
	@SuppressWarnings("null")
	public static final Type<FishingHookedAnimPayload> TYPE = new Type<>(
			ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "fishing_hooked_anim")
	);

	@SuppressWarnings("null")
	public static final StreamCodec<ByteBuf, FishingHookedAnimPayload> STREAM_CODEC = StreamCodec.composite(
			net.minecraft.core.UUIDUtil.STREAM_CODEC, FishingHookedAnimPayload::sessionId,
			ByteBufCodecs.VAR_INT, FishingHookedAnimPayload::durationTicks,
			FishingHookedAnimPayload::new
	);

	@Override
	public @NotNull Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	public static void handle(FishingHookedAnimPayload payload, IPayloadContext context) {
		context.enqueueWork(() -> handleClient(payload));
	}

	@net.neoforged.api.distmarker.OnlyIn(net.neoforged.api.distmarker.Dist.CLIENT)
	private static void handleClient(FishingHookedAnimPayload payload) {
		if (!com.stardew.craft.client.fishing.FishingInteractionState.accepts(payload.sessionId())) return;
		net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
		if (mc == null || mc.player == null) {
			return;
		}
		com.stardew.craft.client.fishing.FishingBiteVisuals.startHookedAnim(payload.durationTicks());
	}
}
