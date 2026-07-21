package com.stardew.craft.server.performance;

import com.stardew.craft.StardewCraft;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Measures server event boundaries on the server thread.
 *
 * <p>This state follows {@link ServerPerformanceRecorder}'s server-thread-only contract.</p>
 */
@EventBusSubscriber(modid = StardewCraft.MODID)
public final class ServerPerformanceEvents {
    private static final Map<UUID, Long> LOGIN_STARTS = new HashMap<>();
    private static long serverTickStart = -1L;

    private ServerPerformanceEvents() {
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onServerTickStart(ServerTickEvent.Pre event) {
        beginServerTick(System.nanoTime());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onServerTickEnd(ServerTickEvent.Post event) {
        endServerTick(System.nanoTime());
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPlayerLoginStart(PlayerEvent.PlayerLoggedInEvent event) {
        beginLogin(event.getEntity().getUUID(), System.nanoTime());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPlayerLoginEnd(PlayerEvent.PlayerLoggedInEvent event) {
        endLogin(event.getEntity().getUUID(), System.nanoTime());
    }

    static void beginServerTick(long now) {
        serverTickStart = now;
    }

    static void endServerTick(long now) {
        if (serverTickStart == -1L) {
            return;
        }

        ServerPerformanceRecorder.record(
            PerformanceTiming.SERVER_TICK,
            Math.max(0L, now - serverTickStart)
        );
        serverTickStart = -1L;
    }

    /**
     * Starts timing a login. A repeated UUID replaces its previous start; the latest event wins.
     */
    static void beginLogin(UUID playerId, long now) {
        LOGIN_STARTS.put(Objects.requireNonNull(playerId, "playerId"), now);
    }

    static void endLogin(UUID playerId, long now) {
        Long startedAt = LOGIN_STARTS.remove(Objects.requireNonNull(playerId, "playerId"));
        if (startedAt == null) {
            return;
        }

        ServerPerformanceRecorder.record(
            PerformanceTiming.PLAYER_LOGIN_EVENT,
            Math.max(0L, now - startedAt)
        );
    }

    static void clearState() {
        LOGIN_STARTS.clear();
        serverTickStart = -1L;
    }
}
