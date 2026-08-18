package com.stardew.craft.server.performance;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    void calculatesNearestRankPercentilesFromUnsortedSamples() {
        RollingTimingWindow window = new RollingTimingWindow(100);
        for (int index = 0; index < 100; index++) {
            long millis = (index * 37L) % 100L + 1L;
            window.record(millis * 1_000_000L);
        }

        TimingSummary summary = window.snapshot();

        assertEquals(95.0D, summary.p95Millis());
        assertEquals(99.0D, summary.p99Millis());
    }

    @Test
    void averagesMaximumLongSamplesWithoutOverflow() {
        RollingTimingWindow window = new RollingTimingWindow(2);
        window.record(Long.MAX_VALUE);
        window.record(Long.MAX_VALUE);

        double averageMillis = window.snapshot().averageMillis();
        double expectedMillis = Long.MAX_VALUE / 1_000_000.0D;

        assertTrue(Double.isFinite(averageMillis));
        assertEquals(expectedMillis, averageMillis, Math.ulp(expectedMillis));
    }

    @Test
    void retainsNewestSamplesAfterMultipleWraps() {
        RollingTimingWindow window = new RollingTimingWindow(3);
        for (long millis = 1L; millis <= 10L; millis++) {
            window.record(millis * 1_000_000L);
        }

        TimingSummary summary = window.snapshot();

        assertEquals(3L, summary.sampleCount());
        assertEquals(9.0D, summary.averageMillis());
        assertEquals(10.0D, summary.maxMillis());
        assertEquals(10.0D, summary.p95Millis());
        assertEquals(10.0D, summary.p99Millis());
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
            PerformanceTiming.PLAYER_LOGIN_SYNC_STAGE,
            PerformanceTiming.CONTENT_SNAPSHOT_BUILD,
            PerformanceTiming.JEI_CATALOG_BUILD,
            PerformanceTiming.FARM_SYNC_CHUNK_LOAD,
            PerformanceTiming.DAILY_SYNC_CHUNK_LOAD,
            PerformanceTiming.DAILY_SETTLEMENT_TOTAL,
            PerformanceTiming.DAILY_SETTLEMENT_TICK,
            PerformanceTiming.DAILY_SETTLEMENT_ATOMIC_ITEM,
            PerformanceTiming.DAILY_SETTLEMENT_LOCK_TO_READY,
            PerformanceTiming.NPC_TICK,
            PerformanceTiming.FESTIVAL_TICK,
            PerformanceTiming.FISHING_TICK,
            PerformanceTiming.CUTSCENE_TRIGGER_SCAN,
            PerformanceTiming.CONTENT_SYNC,
            PerformanceTiming.FARM_DAILY_PROCESS,
            PerformanceTiming.OFFLINE_FARM_CATCH_UP
        }, PerformanceTiming.values());
    }
}
