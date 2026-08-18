package com.stardew.craft.event;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.core.ModDimensions;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

@EventBusSubscriber(modid = StardewCraft.MODID)
public class InteriorMainAreaChunkKeeperEvents {

    // 当前主线地图的稳定落点，作为主区域保活中心。
    private static final int MAIN_AREA_CENTER_X = 150;
    private static final int MAIN_AREA_CENTER_Z = 119;
    private static final int LEGACY_FORCE_RADIUS_CHUNKS = 6;

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        ServerLevel level = event.getServer().getLevel(ModDimensions.STARDEW_VALLEY);
        if (level == null) {
            return;
        }

        // Older builds forced this unrelated 13x13 town area whenever any player
        // entered an interior. Remove those persisted tickets once at startup;
        // NPC route targets now own only the chunks they actually need.
        releaseLegacyMainAreaTickets(level);
    }

    private static void releaseLegacyMainAreaTickets(ServerLevel level) {
        int centerChunkX = MAIN_AREA_CENTER_X >> 4;
        int centerChunkZ = MAIN_AREA_CENTER_Z >> 4;

        for (int dz = -LEGACY_FORCE_RADIUS_CHUNKS; dz <= LEGACY_FORCE_RADIUS_CHUNKS; dz++) {
            for (int dx = -LEGACY_FORCE_RADIUS_CHUNKS; dx <= LEGACY_FORCE_RADIUS_CHUNKS; dx++) {
                int chunkX = centerChunkX + dx;
                int chunkZ = centerChunkZ + dz;
                level.setChunkForced(chunkX, chunkZ, false);
            }
        }
        StardewCraft.LOGGER.info("[INTERIOR] Released legacy main-area chunk tickets");
    }
}
