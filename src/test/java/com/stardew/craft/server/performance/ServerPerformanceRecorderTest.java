package com.stardew.craft.server.performance;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerPerformanceRecorderTest {

    @BeforeEach
    void setUp() {
        ServerPerformanceRecorder.reset();
        ServerPerformanceRecorder.enable();
    }

    @AfterEach
    void tearDown() {
        ServerPerformanceRecorder.disable();
        ServerPerformanceRecorder.reset();
    }

    @Test
    void performanceCountersHaveStableOrder() {
        assertArrayEquals(new PerformanceCounter[] {
            PerformanceCounter.CONTENT_SYNC_RECIPIENTS,
            PerformanceCounter.CONTENT_SYNC_PACKETS,
            PerformanceCounter.CONTENT_REGISTRY_BYTES,
            PerformanceCounter.JEI_CATALOG_ENTRIES,
            PerformanceCounter.PLAYER_FULL_SYNC_REQUESTS,
            PerformanceCounter.PLAYER_FULL_SYNC_SENT,
            PerformanceCounter.PLAYER_FULL_SYNC_SKIPPED,
            PerformanceCounter.PLAYER_LOGIN_SYNC_ENQUEUED,
            PerformanceCounter.PLAYER_LOGIN_SYNC_STAGES,
            PerformanceCounter.PLAYER_LOGIN_SYNC_COMPLETED,
            PerformanceCounter.FARM_SYNC_CHUNK_LOADS,
            PerformanceCounter.DAILY_SYNC_CHUNK_LOADS,
            PerformanceCounter.DAILY_SETTLEMENT_TICKS,
            PerformanceCounter.DAILY_SETTLEMENT_ITEMS,
            PerformanceCounter.DAILY_SETTLEMENT_CHUNK_LEASES,
            PerformanceCounter.DAILY_SETTLEMENT_OVERSHOOTS,
            PerformanceCounter.DAILY_SETTLEMENT_RETRIES,
            PerformanceCounter.DAILY_SETTLEMENT_PERMANENT_FAILURES,
            PerformanceCounter.DAILY_SETTLEMENT_PLAYER_BATCHES,
            PerformanceCounter.DAILY_SETTLEMENT_READY_PUBLICATIONS,
            PerformanceCounter.CONTENT_CACHE_HITS,
            PerformanceCounter.CONTENT_CACHE_REBUILDS,
            PerformanceCounter.FARM_CATCH_UP_CHUNKS,
            PerformanceCounter.FARM_CATCH_UP_OBJECTS
        }, PerformanceCounter.values());
    }

    @Test
    void disabledProfilerSkipsOptionalSamplesAndCounters() {
        ServerPerformanceRecorder.disable();

        long startedAt = ServerPerformanceRecorder.startTiming();
        ServerPerformanceRecorder.finishTiming(PerformanceTiming.NPC_TICK, startedAt);
        ServerPerformanceRecorder.increment(PerformanceCounter.CONTENT_SYNC_PACKETS, 1L);

        PerformanceSnapshot snapshot = ServerPerformanceRecorder.snapshot();
        assertFalse(snapshot.enabled());
        assertEquals(0L, snapshot.timings().get(PerformanceTiming.NPC_TICK).sampleCount());
        assertEquals(0L, snapshot.counters().get(PerformanceCounter.CONTENT_SYNC_PACKETS));
    }

    @Test
    void enableResetsOldSamplesAndAllowsLowOverheadTimingApi() {
        ServerPerformanceRecorder.record(PerformanceTiming.SERVER_TICK, 1L);
        ServerPerformanceRecorder.enable();

        long startedAt = ServerPerformanceRecorder.startTiming();
        ServerPerformanceRecorder.finishTiming(PerformanceTiming.NPC_TICK, startedAt);

        PerformanceSnapshot snapshot = ServerPerformanceRecorder.snapshot();
        assertTrue(snapshot.enabled());
        assertEquals(0L, snapshot.timings().get(PerformanceTiming.SERVER_TICK).sampleCount());
        assertEquals(1L, snapshot.timings().get(PerformanceTiming.NPC_TICK).sampleCount());
    }

    @Test
    void snapshotContainsEveryTimingAndCounter() {
        PerformanceSnapshot snapshot = ServerPerformanceRecorder.snapshot();

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
    void usesPrivateWindowSizeAndPrimitiveCounterStorage() throws NoSuchFieldException {
        Field windowSize = ServerPerformanceRecorder.class.getDeclaredField("TIMING_WINDOW_SIZE");
        Field counters = ServerPerformanceRecorder.class.getDeclaredField("COUNTERS");

        assertTrue(Modifier.isPrivate(windowSize.getModifiers()));
        assertEquals(long[].class, counters.getType());

        for (int sample = 0; sample < 1_201; sample++) {
            ServerPerformanceRecorder.record(PerformanceTiming.SERVER_TICK, sample);
        }
        assertEquals(
            1_200L,
            ServerPerformanceRecorder.snapshot().timings().get(PerformanceTiming.SERVER_TICK).sampleCount()
        );
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
    void runnableMeasurePropagatesExceptionAndStillRecordsTiming() {
        IllegalArgumentException failure = new IllegalArgumentException("failed");
        Runnable operation = () -> {
            throw failure;
        };

        IllegalArgumentException thrown = assertThrows(
            IllegalArgumentException.class,
            () -> ServerPerformanceRecorder.measure(PerformanceTiming.JEI_CATALOG_BUILD, operation)
        );

        assertSame(failure, thrown);
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
    void counterAdditionSaturatesAtLongMaximum() {
        ServerPerformanceRecorder.increment(PerformanceCounter.CONTENT_REGISTRY_BYTES, Long.MAX_VALUE);
        ServerPerformanceRecorder.increment(PerformanceCounter.CONTENT_REGISTRY_BYTES, 1L);

        assertEquals(
            Long.MAX_VALUE,
            ServerPerformanceRecorder.snapshot().counters()
                .get(PerformanceCounter.CONTENT_REGISTRY_BYTES)
        );
    }

    @Test
    void recordRejectsNullTiming() {
        NullPointerException thrown = assertThrows(
            NullPointerException.class,
            () -> ServerPerformanceRecorder.record(null, 1L)
        );

        assertEquals("timing", thrown.getMessage());
    }

    @Test
    void incrementRejectsNullCounterEvenForIgnoredAmount() {
        NullPointerException thrown = assertThrows(
            NullPointerException.class,
            () -> ServerPerformanceRecorder.increment(null, 0L)
        );

        assertEquals("counter", thrown.getMessage());
    }

    @Test
    void supplierMeasureRejectsNullTimingBeforeRunningOperation() {
        AtomicBoolean executed = new AtomicBoolean();
        Supplier<String> operation = () -> {
            executed.set(true);
            return "value";
        };

        NullPointerException thrown = assertThrows(
            NullPointerException.class,
            () -> ServerPerformanceRecorder.measure(null, operation)
        );

        assertEquals("timing", thrown.getMessage());
        assertFalse(executed.get());
    }

    @Test
    void supplierMeasureRejectsNullOperationWithoutRecording() {
        Supplier<String> operation = null;

        NullPointerException thrown = assertThrows(
            NullPointerException.class,
            () -> ServerPerformanceRecorder.measure(PerformanceTiming.CONTENT_SNAPSHOT_BUILD, operation)
        );

        assertEquals("operation", thrown.getMessage());
        assertEquals(
            0L,
            ServerPerformanceRecorder.snapshot().timings()
                .get(PerformanceTiming.CONTENT_SNAPSHOT_BUILD).sampleCount()
        );
    }

    @Test
    void runnableMeasureRejectsNullTimingBeforeRunningOperation() {
        AtomicBoolean executed = new AtomicBoolean();
        Runnable operation = () -> executed.set(true);

        NullPointerException thrown = assertThrows(
            NullPointerException.class,
            () -> ServerPerformanceRecorder.measure(null, operation)
        );

        assertEquals("timing", thrown.getMessage());
        assertFalse(executed.get());
    }

    @Test
    void runnableMeasureRejectsNullOperationWithoutRecording() {
        Runnable operation = null;

        NullPointerException thrown = assertThrows(
            NullPointerException.class,
            () -> ServerPerformanceRecorder.measure(PerformanceTiming.JEI_CATALOG_BUILD, operation)
        );

        assertEquals("operation", thrown.getMessage());
        assertEquals(
            0L,
            ServerPerformanceRecorder.snapshot().timings()
                .get(PerformanceTiming.JEI_CATALOG_BUILD).sampleCount()
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
