package com.stardew.craft.server.performance;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RollingTimingWindowTest {

    @Test
    void retainsNewestSamplesAndSummarizesInMilliseconds() {
        RollingTimingWindow window = new RollingTimingWindow(4);
        window.record(1_000_000L);
        window.record(2_000_000L);
        window.record(3_000_000L);
        window.record(4_000_000L);
        window.record(5_000_000L);

        TimingSummary summary = window.snapshot();

        assertEquals(4L, summary.sampleCount());
        assertEquals(3.5D, summary.averageMillis());
        assertEquals(5.0D, summary.maxMillis());
        assertEquals(5.0D, summary.p95Millis());
        assertEquals(5.0D, summary.p99Millis());
    }

    @Test
    void emptyWindowReturnsZeroSummary() {
        assertEquals(TimingSummary.ZERO, new RollingTimingWindow(4).snapshot());
    }

    @Test
    void rejectsNonPositiveCapacity() {
        IllegalArgumentException zeroCapacity = assertThrows(
            IllegalArgumentException.class,
            () -> new RollingTimingWindow(0)
        );
        IllegalArgumentException negativeCapacity = assertThrows(
            IllegalArgumentException.class,
            () -> new RollingTimingWindow(-1)
        );

        assertEquals("capacity must be positive", zeroCapacity.getMessage());
        assertEquals("capacity must be positive", negativeCapacity.getMessage());
    }

    @Test
    void clampsNegativeSamplesToZero() {
        RollingTimingWindow window = new RollingTimingWindow(1);

        window.record(-1L);

        assertEquals(new TimingSummary(1L, 0.0D, 0.0D, 0.0D, 0.0D), window.snapshot());
    }

    @Test
    void clearResetsSamplesAndWritePosition() {
        RollingTimingWindow window = new RollingTimingWindow(2);
        window.record(1_000_000L);
        window.record(2_000_000L);
        window.record(3_000_000L);

        window.clear();

        assertEquals(TimingSummary.ZERO, window.snapshot());

        window.record(7_000_000L);

        assertEquals(new TimingSummary(1L, 7.0D, 7.0D, 7.0D, 7.0D), window.snapshot());
    }

    @Test
    void performanceTimingsHaveStableOrder() {
        assertArrayEquals(new PerformanceTiming[] {
            PerformanceTiming.SERVER_TICK,
            PerformanceTiming.PLAYER_LOGIN_EVENT,
            PerformanceTiming.CONTENT_SNAPSHOT_BUILD,
            PerformanceTiming.JEI_CATALOG_BUILD,
            PerformanceTiming.FARM_SYNC_CHUNK_LOAD,
            PerformanceTiming.DAILY_SYNC_CHUNK_LOAD
        }, PerformanceTiming.values());
    }
}
