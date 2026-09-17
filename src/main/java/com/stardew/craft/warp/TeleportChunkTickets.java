package com.stardew.craft.warp;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

/** Transient, reference-counted FULL tickets. Never touches persistent /forceload state. */
@net.neoforged.fml.common.EventBusSubscriber(modid = com.stardew.craft.StardewCraft.MODID)
public final class TeleportChunkTickets {
    private static final TicketType<ChunkPos> TYPE = TicketType.create(
            "stardewcraft_player_teleport", java.util.Comparator.comparingLong(ChunkPos::toLong));
    private static final Map<ServerLevel, Map<ChunkPos, Integer>> HELD = new IdentityHashMap<>();
    private TeleportChunkTickets() {}

    @net.neoforged.bus.api.SubscribeEvent
    public static void stop(net.neoforged.neoforge.event.server.ServerStoppedEvent event) {
        for (var entry : HELD.entrySet())
            for (var chunk : entry.getValue().keySet())
                entry.getKey().getChunkSource().removeRegionTicket(TYPE, chunk, 0, chunk);
        HELD.clear();
    }

    public static boolean acquire(ServerLevel level, ChunkPos chunk) {
        Map<ChunkPos, Integer> counts = HELD.computeIfAbsent(level, ignored -> new HashMap<>());
        if (!counts.containsKey(chunk)) level.getChunkSource().addRegionTicket(TYPE, chunk, 0, chunk);
        counts.merge(chunk, 1, Integer::sum);
        return true;
    }

    public static void release(ServerLevel level, ChunkPos chunk) {
        Map<ChunkPos, Integer> counts = HELD.get(level);
        if (counts == null || !counts.containsKey(chunk)) return;
        int remaining = counts.get(chunk) - 1;
        if (remaining > 0) counts.put(chunk, remaining);
        else {
            level.getChunkSource().removeRegionTicket(TYPE, chunk, 0, chunk);
            counts.remove(chunk);
            if (counts.isEmpty()) HELD.remove(level);
        }
    }
}
