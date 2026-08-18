package com.stardew.craft.event;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.core.ModDimensions;
import com.stardew.craft.fishing.splash.FishSplashState;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkWatchEvent;

@EventBusSubscriber(modid = StardewCraft.MODID)
public final class FishSplashSyncEvents {
    private FishSplashSyncEvents() {
    }

    @SubscribeEvent
    public static void onChunkSent(ChunkWatchEvent.Sent event) {
        ServerLevel level = event.getLevel();
        if (!level.dimension().equals(ModDimensions.STARDEW_VALLEY)
                || event.getPlayer().serverLevel() != level) {
            return;
        }
        FishSplashState.get(level).sendChunkSnapshot(event.getPlayer(), event.getPos());
    }

    @SubscribeEvent
    public static void onChunkUnwatched(ChunkWatchEvent.UnWatch event) {
        ServerLevel level = event.getLevel();
        if (level.dimension().equals(ModDimensions.STARDEW_VALLEY)) {
            FishSplashState.get(level).clearChunk(event.getPlayer(), event.getPos());
        }
    }

    @EventBusSubscriber(modid = StardewCraft.MODID, value = Dist.CLIENT)
    public static final class ClientEvents {
        private ClientEvents() {
        }

        @SubscribeEvent
        public static void onClientDisconnect(
                net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut event
        ) {
            com.stardew.craft.client.fishing.ClientFishSplashState.clearAll();
        }
    }
}
