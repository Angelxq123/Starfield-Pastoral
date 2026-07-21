package com.stardew.craft.server.performance;

import com.stardew.craft.StardewCraft;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Measures server event boundaries on the server thread.
 *
 * <p>Measurements are priority-bounded approximations: they include work dispatched between the
 * highest-priority start and lowest-priority end handlers, but not work outside those boundaries.
 * Tick timing and each player's login timing are non-reentrant; a repeated begin replaces the
 * active start, so the latest begin wins.</p>
 *
 * <p>This state follows {@link ServerPerformanceRecorder}'s server-thread-only contract.</p>
 */
@EventBusSubscriber(modid = StardewCraft.MODID)
public final class ServerPerformanceEvents {
    private static final long LOGIN_TIMEOUT_NANOS = 5L * 60L * 1_000_000_000L;
    private static final int MAX_PENDING_LOGINS = 256;
    private static final Map<UUID, Long> LOGIN_STARTS = new HashMap<>();
    private static long serverTickStart;
    private static boolean serverTickActive;

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

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        removeLogin(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        clearState();
    }

    /**
     * Starts non-reentrant tick timing. A repeated call replaces the start; the latest begin wins.
     */
    static void beginServerTick(long now) {
        serverTickStart = now;
        serverTickActive = true;
    }

    static void endServerTick(long now) {
        if (!serverTickActive) {
            return;
        }

        ServerPerformanceRecorder.record(
            PerformanceTiming.SERVER_TICK,
            Math.max(0L, now - serverTickStart)
        );
        serverTickStart = 0L;
        serverTickActive = false;
    }

    /**
     * Starts non-reentrant timing for a login. A repeated UUID replaces its previous start; the
     * latest begin wins.
     */
    static void beginLogin(UUID playerId, long now) {
        UUID validatedPlayerId = Objects.requireNonNull(playerId, "playerId");
        purgeStaleLogins(now);
        if (!LOGIN_STARTS.containsKey(validatedPlayerId)
            && LOGIN_STARTS.size() >= MAX_PENDING_LOGINS) {
            evictOldestLogin(now);
        }
        LOGIN_STARTS.put(validatedPlayerId, now);
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

    static void removeLogin(UUID playerId) {
        LOGIN_STARTS.remove(Objects.requireNonNull(playerId, "playerId"));
    }

    static void purgeStaleLogins(long now) {
        Iterator<Map.Entry<UUID, Long>> iterator = LOGIN_STARTS.entrySet().iterator();
        while (iterator.hasNext()) {
            long startedAt = iterator.next().getValue();
            // nanoTime differences remain wrap-safe for intervals shorter than 2^63 nanoseconds.
            long elapsed = now - startedAt;
            if (elapsed >= LOGIN_TIMEOUT_NANOS) {
                iterator.remove();
            }
        }
    }

    static int pendingLoginCount() {
        return LOGIN_STARTS.size();
    }

    static void clearState() {
        LOGIN_STARTS.clear();
        serverTickStart = 0L;
        serverTickActive = false;
    }

    private static void evictOldestLogin(long now) {
        UUID oldestPlayerId = null;
        long longestElapsed = Long.MIN_VALUE;
        for (Map.Entry<UUID, Long> entry : LOGIN_STARTS.entrySet()) {
            long elapsed = now - entry.getValue();
            if (oldestPlayerId == null || elapsed > longestElapsed) {
                oldestPlayerId = entry.getKey();
                longestElapsed = elapsed;
            }
        }

        if (oldestPlayerId != null) {
            LOGIN_STARTS.remove(oldestPlayerId);
        }
    }
}
