package com.stardew.craft.server.performance;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerPerformanceRecorderTest {

    @BeforeEach
    void setUp() {
        ServerPerformanceRecorder.reset();
    }

    @AfterEach
    void tearDown() {
        ServerPerformanceRecorder.reset();
    }

    @Test
    void performanceCountersHaveStableOrder() {
        assertArrayEquals(new PerformanceCounter[] {
            PerformanceCounter.CONTENT_SYNC_RECIPIENTS,
            PerformanceCounter.CONTENT_SYNC_PACKETS,
            PerformanceCounter.CONTENT_REGISTRY_BYTES,
            PerformanceCounter.JEI_CATALOG_ENTRIES,
            PerformanceCounter.FARM_SYNC_CHUNK_LOADS,
            PerformanceCounter.DAILY_SYNC_CHUNK_LOADS
        }, PerformanceCounter.values());
    }

    @Test
    void snapshotContainsEveryTimingAndCounter() {
        PerformanceSnapshot snapshot = ServerPerformanceRecorder.snapshot();

        assertEquals(1_200, ServerPerformanceRecorder.TIMING_WINDOW_SIZE);
        assertEquals(PerformanceTiming.values().length, snapshot.timings().size());
        assertEquals(PerformanceCounter.values().length, snapshot.counters().size());
        for (PerformanceTiming timing : PerformanceTiming.values()) {
            assertEquals(TimingSummary.ZERO, snapshot.timings().get(timing));
        }
        for (PerformanceCounter counter : PerformanceCounter.values()) {
            assertEquals(0L, snapshot.counters().get(counter));
        }
    }

    @Test
    void recordsTimingAndAccumulatesCounter() {
        ServerPerformanceRecorder.record(PerformanceTiming.SERVER_TICK, 20_000_000L);
        ServerPerformanceRecorder.increment(PerformanceCounter.CONTENT_SYNC_PACKETS, 4L);
        ServerPerformanceRecorder.increment(PerformanceCounter.CONTENT_SYNC_PACKETS, 2L);

        PerformanceSnapshot snapshot = ServerPerformanceRecorder.snapshot();
        TimingSummary summary = snapshot.timings().get(PerformanceTiming.SERVER_TICK);

        assertEquals(1L, summary.sampleCount());
        assertEquals(20.0D, summary.averageMillis());
        assertEquals(6L, snapshot.counters().get(PerformanceCounter.CONTENT_SYNC_PACKETS));
    }

    @Test
    void measureReturnsSupplierValueAndRecordsTiming() {
        String result = ServerPerformanceRecorder.measure(
            PerformanceTiming.CONTENT_SNAPSHOT_BUILD,
            () -> "snapshot"
        );

        assertEquals("snapshot", result);
        assertEquals(
            1L,
            ServerPerformanceRecorder.snapshot().timings()
                .get(PerformanceTiming.CONTENT_SNAPSHOT_BUILD).sampleCount()
        );
    }

    @Test
    void runnableMeasureExecutesAndRecordsTiming() {
        AtomicBoolean executed = new AtomicBoolean();

        ServerPerformanceRecorder.measure(PerformanceTiming.JEI_CATALOG_BUILD, () -> executed.set(true));

        assertTrue(executed.get());
        assertEquals(
            1L,
            ServerPerformanceRecorder.snapshot().timings()
                .get(PerformanceTiming.JEI_CATALOG_BUILD).sampleCount()
        );
    }

    @Test
    void resetClearsTimingsAndZerosEveryCounter() {
        for (PerformanceTiming timing : PerformanceTiming.values()) {
            ServerPerformanceRecorder.record(timing, 1_000_000L);
        }
        for (PerformanceCounter counter : PerformanceCounter.values()) {
            ServerPerformanceRecorder.increment(counter, 3L);
        }

        ServerPerformanceRecorder.reset();

        PerformanceSnapshot snapshot = ServerPerformanceRecorder.snapshot();
        for (PerformanceTiming timing : PerformanceTiming.values()) {
            assertEquals(TimingSummary.ZERO, snapshot.timings().get(timing));
        }
        for (PerformanceCounter counter : PerformanceCounter.values()) {
            assertEquals(0L, snapshot.counters().get(counter));
        }
    }

    @Test
    void ignoresNonPositiveCounterIncrements() {
        ServerPerformanceRecorder.increment(PerformanceCounter.CONTENT_REGISTRY_BYTES, 5L);
        ServerPerformanceRecorder.increment(PerformanceCounter.CONTENT_REGISTRY_BYTES, 0L);
        ServerPerformanceRecorder.increment(PerformanceCounter.CONTENT_REGISTRY_BYTES, -2L);

        assertEquals(
            5L,
            ServerPerformanceRecorder.snapshot().counters()
                .get(PerformanceCounter.CONTENT_REGISTRY_BYTES)
        );
    }

    @Test
    void measurePropagatesSupplierExceptionAndStillRecordsTiming() {
        IllegalStateException failure = new IllegalStateException("failed");

        IllegalStateException thrown = assertThrows(
            IllegalStateException.class,
            () -> ServerPerformanceRecorder.measure(
                PerformanceTiming.CONTENT_SNAPSHOT_BUILD,
                () -> {
                    throw failure;
                }
            )
        );

        assertSame(failure, thrown);
        assertEquals(
            1L,
            ServerPerformanceRecorder.snapshot().timings()
                .get(PerformanceTiming.CONTENT_SNAPSHOT_BUILD).sampleCount()
        );
    }

    @Test
    void snapshotMapsAreImmutableAndRemainStableAfterRecorderChanges() {
        ServerPerformanceRecorder.record(PerformanceTiming.SERVER_TICK, 20_000_000L);
        ServerPerformanceRecorder.increment(PerformanceCounter.CONTENT_SYNC_PACKETS, 6L);
        PerformanceSnapshot previous = ServerPerformanceRecorder.snapshot();

        assertThrows(
            UnsupportedOperationException.class,
            () -> previous.timings().put(PerformanceTiming.SERVER_TICK, TimingSummary.ZERO)
        );
        assertThrows(
            UnsupportedOperationException.class,
            () -> previous.counters().put(PerformanceCounter.CONTENT_SYNC_PACKETS, 0L)
        );

        ServerPerformanceRecorder.record(PerformanceTiming.SERVER_TICK, 40_000_000L);
        ServerPerformanceRecorder.increment(PerformanceCounter.CONTENT_SYNC_PACKETS, 3L);
        ServerPerformanceRecorder.reset();

        assertEquals(1L, previous.timings().get(PerformanceTiming.SERVER_TICK).sampleCount());
        assertEquals(20.0D, previous.timings().get(PerformanceTiming.SERVER_TICK).averageMillis());
        assertEquals(6L, previous.counters().get(PerformanceCounter.CONTENT_SYNC_PACKETS));
    }

    @Test
    void snapshotRecordDefensivelyCopiesConstructorMaps() {
        EnumMap<PerformanceTiming, TimingSummary> timings = new EnumMap<>(PerformanceTiming.class);
        timings.put(PerformanceTiming.SERVER_TICK, TimingSummary.ZERO);
        EnumMap<PerformanceCounter, Long> counters = new EnumMap<>(PerformanceCounter.class);
        counters.put(PerformanceCounter.CONTENT_SYNC_PACKETS, 4L);

        PerformanceSnapshot snapshot = new PerformanceSnapshot(timings, counters);
        timings.clear();
        counters.clear();

        assertEquals(Map.of(PerformanceTiming.SERVER_TICK, TimingSummary.ZERO), snapshot.timings());
        assertEquals(Map.of(PerformanceCounter.CONTENT_SYNC_PACKETS, 4L), snapshot.counters());
        assertThrows(UnsupportedOperationException.class, snapshot.timings()::clear);
        assertThrows(UnsupportedOperationException.class, snapshot.counters()::clear);
    }
}
