package com.stardew.craft.server.performance;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Collects server performance timings and counters.
 *
 * <p>This recorder is not thread-safe and must only be accessed from the server thread.</p>
 */
public final class ServerPerformanceRecorder {
    private static final int TIMING_WINDOW_SIZE = 1_200;

    private static final EnumMap<PerformanceTiming, RollingTimingWindow> TIMINGS =
        new EnumMap<>(PerformanceTiming.class);
    private static final long[] COUNTERS = new long[PerformanceCounter.values().length];

    static {
        for (PerformanceTiming timing : PerformanceTiming.values()) {
            TIMINGS.put(timing, new RollingTimingWindow(TIMING_WINDOW_SIZE));
        }
    }

    private ServerPerformanceRecorder() {
    }

    /**
     * Records a duration for the supplied timing category on the server thread.
     */
    public static void record(PerformanceTiming timing, long nanoseconds) {
        Objects.requireNonNull(timing, "timing");
        TIMINGS.get(timing).record(nanoseconds);
    }

    /**
     * Adds a positive amount to a counter on the server thread, saturating at {@link Long#MAX_VALUE}.
     * Nonpositive increments are ignored.
     */
    public static void increment(PerformanceCounter counter, long amount) {
        Objects.requireNonNull(counter, "counter");
        if (amount <= 0L) {
            return;
        }

        int index = counter.ordinal();
        long current = COUNTERS[index];
        COUNTERS[index] = current > Long.MAX_VALUE - amount
            ? Long.MAX_VALUE
            : current + amount;
    }

    /**
     * Runs a value-returning operation on the server thread and records its duration.
     */
    public static <T> T measure(PerformanceTiming timing, Supplier<T> operation) {
        Objects.requireNonNull(timing, "timing");
        Objects.requireNonNull(operation, "operation");
        long startedAt = System.nanoTime();
        try {
            return operation.get();
        } finally {
            record(timing, System.nanoTime() - startedAt);
        }
    }

    /**
     * Runs an operation on the server thread and records its duration.
     */
    public static void measure(PerformanceTiming timing, Runnable operation) {
        Objects.requireNonNull(timing, "timing");
        Objects.requireNonNull(operation, "operation");
        long startedAt = System.nanoTime();
        try {
            operation.run();
        } finally {
            record(timing, System.nanoTime() - startedAt);
        }
    }

    /**
     * Returns an immutable snapshot of every timing and counter category.
     */
    public static PerformanceSnapshot snapshot() {
        EnumMap<PerformanceTiming, TimingSummary> timingSnapshots =
            new EnumMap<>(PerformanceTiming.class);
        for (PerformanceTiming timing : PerformanceTiming.values()) {
            timingSnapshots.put(timing, TIMINGS.get(timing).snapshot());
        }

        EnumMap<PerformanceCounter, Long> counterSnapshots =
            new EnumMap<>(PerformanceCounter.class);
        for (PerformanceCounter counter : PerformanceCounter.values()) {
            counterSnapshots.put(counter, COUNTERS[counter.ordinal()]);
        }
        return new PerformanceSnapshot(timingSnapshots, counterSnapshots);
    }

    /**
     * Clears all timings and counters on the server thread.
     */
    public static void reset() {
        for (RollingTimingWindow timingWindow : TIMINGS.values()) {
            timingWindow.clear();
        }
        Arrays.fill(COUNTERS, 0L);
    }
}
