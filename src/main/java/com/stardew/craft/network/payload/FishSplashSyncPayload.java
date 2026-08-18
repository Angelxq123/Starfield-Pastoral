package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.client.fishing.ClientFishSplashState;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Server → Client: sync fish splash points (气泡).
 * <p>
 * If {@code fullSnapshot=true}, the client replaces its entire splash map with
 * the supplied entries. Empty optionals are skipped on snapshot apply.
 * <p>
 * If {@code fullSnapshot=false}, each entry is a diff:
 * {@code Optional.empty()} = remove that locationKey, present = add/replace.
 */
public record FishSplashSyncPayload(
		boolean fullSnapshot,
		boolean chunkSnapshot,
		int chunkX,
		int chunkZ,
		Map<String, Optional<BlockPos>> entries)
		implements CustomPacketPayload {

	public static final Type<FishSplashSyncPayload> TYPE = new Type<>(
			ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "fish_splash_sync"));

	public static final StreamCodec<ByteBuf, FishSplashSyncPayload> STREAM_CODEC =
			new StreamCodec<>() {
				@Override
				public FishSplashSyncPayload decode(ByteBuf buf) {
					boolean fullSnapshot = buf.readBoolean();
					boolean chunkSnapshot = buf.readBoolean();
					int chunkX = ByteBufCodecs.INT.decode(buf);
					int chunkZ = ByteBufCodecs.INT.decode(buf);
					int entryCount = ByteBufCodecs.VAR_INT.decode(buf);
					Map<String, Optional<BlockPos>> entries = new LinkedHashMap<>(entryCount);
					for (int index = 0; index < entryCount; index++) {
						String key = ByteBufCodecs.STRING_UTF8.decode(buf);
						Optional<BlockPos> pos = buf.readBoolean()
								? Optional.of(BlockPos.STREAM_CODEC.decode(buf))
								: Optional.empty();
						entries.put(key, pos);
					}
					return new FishSplashSyncPayload(
							fullSnapshot, chunkSnapshot, chunkX, chunkZ, entries);
				}

				@Override
				public void encode(ByteBuf buf, FishSplashSyncPayload payload) {
					buf.writeBoolean(payload.fullSnapshot);
					buf.writeBoolean(payload.chunkSnapshot);
					ByteBufCodecs.INT.encode(buf, payload.chunkX);
					ByteBufCodecs.INT.encode(buf, payload.chunkZ);
					ByteBufCodecs.VAR_INT.encode(buf, payload.entries.size());
					for (Map.Entry<String, Optional<BlockPos>> entry : payload.entries.entrySet()) {
						ByteBufCodecs.STRING_UTF8.encode(buf, entry.getKey());
						buf.writeBoolean(entry.getValue().isPresent());
						entry.getValue().ifPresent(pos -> BlockPos.STREAM_CODEC.encode(buf, pos));
					}
				}
			};

	public FishSplashSyncPayload {
		entries = Map.copyOf(entries);
	}

	/** Convenience: build a snapshot payload from a present-only map. */
	public static FishSplashSyncPayload snapshot(Map<String, BlockPos> snapshot) {
		LinkedHashMap<String, Optional<BlockPos>> wrapped = new LinkedHashMap<>(snapshot.size());
		for (Map.Entry<String, BlockPos> e : snapshot.entrySet()) {
			wrapped.put(e.getKey(), Optional.of(e.getValue()));
		}
		return new FishSplashSyncPayload(true, false, 0, 0, wrapped);
	}

	public static FishSplashSyncPayload chunkSnapshot(
			int chunkX,
			int chunkZ,
			Map<String, BlockPos> snapshot
	) {
		LinkedHashMap<String, Optional<BlockPos>> wrapped = new LinkedHashMap<>(snapshot.size());
		for (Map.Entry<String, BlockPos> entry : snapshot.entrySet()) {
			wrapped.put(entry.getKey(), Optional.of(entry.getValue()));
		}
		return new FishSplashSyncPayload(false, true, chunkX, chunkZ, wrapped);
	}

	/** Convenience: build a diff payload (additions + removals). */
	public static FishSplashSyncPayload diff(Map<String, BlockPos> additions, java.util.Set<String> removals) {
		LinkedHashMap<String, Optional<BlockPos>> wrapped = new LinkedHashMap<>(additions.size() + removals.size());
		for (Map.Entry<String, BlockPos> e : additions.entrySet()) {
			wrapped.put(e.getKey(), Optional.of(e.getValue()));
		}
		for (String key : removals) {
			wrapped.put(key, Optional.empty());
		}
		return new FishSplashSyncPayload(false, false, 0, 0, wrapped);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	@SuppressWarnings("null")
	public static void handle(FishSplashSyncPayload payload, IPayloadContext context) {
		context.enqueueWork(() -> {
			if (payload.fullSnapshot) {
				LinkedHashMap<String, BlockPos> flat = new LinkedHashMap<>(payload.entries.size());
				for (Map.Entry<String, Optional<BlockPos>> e : payload.entries.entrySet()) {
					e.getValue().ifPresent(v -> flat.put(e.getKey(), v));
				}
				ClientFishSplashState.replaceAll(flat);
			} else if (payload.chunkSnapshot) {
				LinkedHashMap<String, BlockPos> flat = new LinkedHashMap<>(payload.entries.size());
				for (Map.Entry<String, Optional<BlockPos>> entry : payload.entries.entrySet()) {
					entry.getValue().ifPresent(pos -> flat.put(entry.getKey(), pos));
				}
				ClientFishSplashState.replaceChunk(payload.chunkX, payload.chunkZ, flat);
			} else {
				for (Map.Entry<String, Optional<BlockPos>> e : payload.entries.entrySet()) {
					if (e.getValue().isEmpty()) {
						ClientFishSplashState.remove(e.getKey());
					} else {
						ClientFishSplashState.put(e.getKey(), e.getValue().get());
					}
				}
			}
		});
	}
}
