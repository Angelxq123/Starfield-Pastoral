package com.stardew.craft.server.performance;

import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ServerPerformanceEventsTest {

    @AfterEach
    void resetState() {
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
    void rejectsNullPlayerIds() {
        assertThrows(
            NullPointerException.class,
            () -> ServerPerformanceEvents.beginLogin(null, 100L)
        );
        assertThrows(
            NullPointerException.class,
            () -> ServerPerformanceEvents.endLogin(null, 150L)
        );
    }

    private static TimingSummary timing(PerformanceTiming timing) {
        return ServerPerformanceRecorder.snapshot().timings().get(timing);
    }
}
