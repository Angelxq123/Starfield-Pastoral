package com.stardew.craft.server.performance;

import com.stardew.craft.StardewCraft;
import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;
import java.util.UUID;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ServerPerformanceEventsTest {
    private static final long LOGIN_TIMEOUT_NANOS = TimeUnit.MINUTES.toNanos(5L);

    @BeforeEach
    void resetStateBeforeTest() {
        resetState();
        ServerPerformanceRecorder.enable();
    }

    @AfterEach
    void resetStateAfterTest() {
        ServerPerformanceRecorder.disable();
        resetState();
    }

    private static void resetState() {
        ServerPerformanceEvents.clearState();
        ServerPerformanceRecorder.reset();
    }

    @Test
    void recordsServerTickDuration() {
        ServerPerformanceEvents.beginServerTick(10L);

        ServerPerformanceEvents.endServerTick(25L);

        TimingSummary summary = timing(PerformanceTiming.SERVER_TICK);
        assertEquals(1L, summary.sampleCount());
        assertEquals(0.000015D, summary.averageMillis());
    }

    @Test
    void recordsMinusOneNanoTimeStartWhenActive() {
        ServerPerformanceEvents.beginServerTick(-1L);

        ServerPerformanceEvents.endServerTick(14L);

        TimingSummary summary = timing(PerformanceTiming.SERVER_TICK);
        assertEquals(1L, summary.sampleCount());
        assertEquals(0.000015D, summary.averageMillis());
    }

    @Test
    void tracksOverlappingLoginsByPlayer() {
        UUID firstPlayer = UUID.randomUUID();
        UUID secondPlayer = UUID.randomUUID();
        ServerPerformanceEvents.beginLogin(firstPlayer, 100L);
        ServerPerformanceEvents.beginLogin(secondPlayer, 120L);

        ServerPerformanceEvents.endLogin(secondPlayer, 150L);
        ServerPerformanceEvents.endLogin(firstPlayer, 180L);

        TimingSummary summary = timing(PerformanceTiming.PLAYER_LOGIN_EVENT);
        assertEquals(2L, summary.sampleCount());
        assertEquals(0.000055D, summary.averageMillis());
    }

    @Test
    void ignoresUnmatchedEnds() {
        ServerPerformanceEvents.endServerTick(25L);
        ServerPerformanceEvents.endLogin(UUID.randomUUID(), 150L);

        assertEquals(TimingSummary.ZERO, timing(PerformanceTiming.SERVER_TICK));
        assertEquals(TimingSummary.ZERO, timing(PerformanceTiming.PLAYER_LOGIN_EVENT));
    }

    @Test
    void clampsNegativeElapsedTimeToZero() {
        UUID playerId = UUID.randomUUID();
        ServerPerformanceEvents.beginServerTick(25L);
        ServerPerformanceEvents.beginLogin(playerId, 150L);

        ServerPerformanceEvents.endServerTick(10L);
        ServerPerformanceEvents.endLogin(playerId, 120L);

        assertEquals(
            new TimingSummary(1L, 0.0D, 0.0D, 0.0D, 0.0D),
            timing(PerformanceTiming.SERVER_TICK)
        );
        assertEquals(
            new TimingSummary(1L, 0.0D, 0.0D, 0.0D, 0.0D),
            timing(PerformanceTiming.PLAYER_LOGIN_EVENT)
        );
    }

    @Test
    void clearStateDiscardsPendingEvents() {
        UUID playerId = UUID.randomUUID();
        ServerPerformanceEvents.beginServerTick(10L);
        ServerPerformanceEvents.beginLogin(playerId, 100L);

        ServerPerformanceEvents.clearState();
        ServerPerformanceEvents.endServerTick(25L);
        ServerPerformanceEvents.endLogin(playerId, 150L);

        assertEquals(TimingSummary.ZERO, timing(PerformanceTiming.SERVER_TICK));
        assertEquals(TimingSummary.ZERO, timing(PerformanceTiming.PLAYER_LOGIN_EVENT));
    }

    @Test
    void repeatedLoginBeginUsesLatestTimestamp() {
        UUID playerId = UUID.randomUUID();
        ServerPerformanceEvents.beginLogin(playerId, 100L);
        ServerPerformanceEvents.beginLogin(playerId, 120L);

        ServerPerformanceEvents.endLogin(playerId, 150L);

        TimingSummary summary = timing(PerformanceTiming.PLAYER_LOGIN_EVENT);
        assertEquals(1L, summary.sampleCount());
        assertEquals(0.00003D, summary.averageMillis());
    }

    @Test
    void loginBeginPurgesEntriesAtTimeoutAcrossNanoTimeWrap() {
        UUID stalePlayer = UUID.randomUUID();
        UUID currentPlayer = UUID.randomUUID();
        long staleStart = Long.MAX_VALUE - 100L;
        long currentStart = staleStart + LOGIN_TIMEOUT_NANOS;
        ServerPerformanceEvents.beginLogin(stalePlayer, staleStart);

        ServerPerformanceEvents.beginLogin(currentPlayer, currentStart);

        assertEquals(1, ServerPerformanceEvents.pendingLoginCount());
        ServerPerformanceEvents.endLogin(stalePlayer, currentStart + 1L);
        assertEquals(TimingSummary.ZERO, timing(PerformanceTiming.PLAYER_LOGIN_EVENT));
    }

    @Test
    void purgeKeepsLoginYoungerThanTimeout() {
        UUID playerId = UUID.randomUUID();
        long startedAt = Long.MIN_VALUE + 100L;
        ServerPerformanceEvents.beginLogin(playerId, startedAt);

        ServerPerformanceEvents.purgeStaleLogins(startedAt + LOGIN_TIMEOUT_NANOS - 1L);

        assertEquals(1, ServerPerformanceEvents.pendingLoginCount());
    }

    @Test
    void pendingLoginsStayBoundedAndEvictOldestStart() {
        UUID oldestPlayer = new UUID(0L, 0L);
        UUID recentPlayer = new UUID(0L, 1L);
        for (int index = 0; index < 256; index++) {
            ServerPerformanceEvents.beginLogin(new UUID(0L, index), 1_000L + index);
        }

        ServerPerformanceEvents.beginLogin(new UUID(0L, 256L), 1_256L);

        assertEquals(256, ServerPerformanceEvents.pendingLoginCount());
        ServerPerformanceEvents.endLogin(oldestPlayer, 1_300L);
        ServerPerformanceEvents.endLogin(recentPlayer, 1_300L);
        assertEquals(1L, timing(PerformanceTiming.PLAYER_LOGIN_EVENT).sampleCount());
    }

    @Test
    void removeLoginDiscardsPendingStart() {
        UUID playerId = UUID.randomUUID();
        ServerPerformanceEvents.beginLogin(playerId, 100L);

        ServerPerformanceEvents.removeLogin(playerId);
        ServerPerformanceEvents.endLogin(playerId, 150L);

        assertEquals(0, ServerPerformanceEvents.pendingLoginCount());
        assertEquals(TimingSummary.ZERO, timing(PerformanceTiming.PLAYER_LOGIN_EVENT));
    }

    @Test
    void serverStoppingClearsPendingState() {
        UUID playerId = UUID.randomUUID();
        ServerPerformanceEvents.beginServerTick(10L);
        ServerPerformanceEvents.beginLogin(playerId, 100L);

        ServerPerformanceEvents.onServerStopping(null);
        ServerPerformanceEvents.endServerTick(25L);
        ServerPerformanceEvents.endLogin(playerId, 150L);

        assertEquals(0, ServerPerformanceEvents.pendingLoginCount());
        assertEquals(TimingSummary.ZERO, timing(PerformanceTiming.SERVER_TICK));
        assertEquals(TimingSummary.ZERO, timing(PerformanceTiming.PLAYER_LOGIN_EVENT));
    }

    @Test
    void declaresSubscriberAndBoundaryPriorities() throws ReflectiveOperationException {
        EventBusSubscriber subscriber = ServerPerformanceEvents.class.getAnnotation(
            EventBusSubscriber.class
        );

        assertNotNull(subscriber);
        assertEquals(StardewCraft.MODID, subscriber.modid());
        assertPriority("onServerTickStart", ServerTickEvent.Pre.class, EventPriority.HIGHEST);
        assertPriority("onServerTickEnd", ServerTickEvent.Post.class, EventPriority.LOWEST);
        assertPriority(
            "onPlayerLoginStart",
            PlayerEvent.PlayerLoggedInEvent.class,
            EventPriority.HIGHEST
        );
        assertPriority(
            "onPlayerLoginEnd",
            PlayerEvent.PlayerLoggedInEvent.class,
            EventPriority.LOWEST
        );
    }

    @Test
    void declaresLogoutAndServerStoppingHandlers() throws ReflectiveOperationException {
        assertSubscribed("onPlayerLogout", PlayerEvent.PlayerLoggedOutEvent.class);
        assertSubscribed("onServerStopping", ServerStoppingEvent.class);
    }

    @Test
    void rejectsNullPlayerIds() {
        assertThrows(
            NullPointerException.class,
            () -> ServerPerformanceEvents.beginLogin(null, 100L)
        );
        assertThrows(
            NullPointerException.class,
            () -> ServerPerformanceEvents.endLogin(null, 150L)
        );
        assertThrows(
            NullPointerException.class,
            () -> ServerPerformanceEvents.removeLogin(null)
        );
    }

    private static TimingSummary timing(PerformanceTiming timing) {
        return ServerPerformanceRecorder.snapshot().timings().get(timing);
    }

    private static void assertPriority(
        String methodName,
        Class<?> eventType,
        EventPriority expected
    ) throws ReflectiveOperationException {
        SubscribeEvent annotation = subscribedMethod(methodName, eventType).getAnnotation(
            SubscribeEvent.class
        );
        assertEquals(expected, annotation.priority());
    }

    private static void assertSubscribed(
        String methodName,
        Class<?> eventType
    ) throws ReflectiveOperationException {
        assertNotNull(subscribedMethod(methodName, eventType).getAnnotation(SubscribeEvent.class));
    }

    private static Method subscribedMethod(
        String methodName,
        Class<?> eventType
    ) throws ReflectiveOperationException {
        Method method = ServerPerformanceEvents.class.getDeclaredMethod(methodName, eventType);
        assertNotNull(method.getAnnotation(SubscribeEvent.class));
        return method;
    }
}
