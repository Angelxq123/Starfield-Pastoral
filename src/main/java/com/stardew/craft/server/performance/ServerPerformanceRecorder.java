package com.stardew.craft.server.performance;

import java.util.EnumMap;
import java.util.function.Supplier;

public final class ServerPerformanceRecorder {
    public static final int TIMING_WINDOW_SIZE = 1_200;

    private static final EnumMap<PerformanceTiming, RollingTimingWindow> TIMINGS =
        new EnumMap<>(PerformanceTiming.class);
    private static final EnumMap<PerformanceCounter, Long> COUNTERS =
        new EnumMap<>(PerformanceCounter.class);

    static {
        for (PerformanceTiming timing : PerformanceTiming.values()) {
            TIMINGS.put(timing, new RollingTimingWindow(TIMING_WINDOW_SIZE));
        }
        for (PerformanceCounter counter : PerformanceCounter.values()) {
            COUNTERS.put(counter, 0L);
        }
    }

    private ServerPerformanceRecorder() {
    }

    public static void record(PerformanceTiming timing, long nanoseconds) {
        TIMINGS.get(timing).record(nanoseconds);
    }

    public static void increment(PerformanceCounter counter, long amount) {
        if (amount > 0L) {
            COUNTERS.merge(counter, amount, Long::sum);
        }
    }

    public static <T> T measure(PerformanceTiming timing, Supplier<T> supplier) {
        long startedAt = System.nanoTime();
        try {
            return supplier.get();
        } finally {
            record(timing, System.nanoTime() - startedAt);
        }
    }

    public static void measure(PerformanceTiming timing, Runnable runnable) {
        measure(timing, () -> {
            runnable.run();
            return null;
        });
    }

    public static PerformanceSnapshot snapshot() {
        EnumMap<PerformanceTiming, TimingSummary> timingSnapshots =
            new EnumMap<>(PerformanceTiming.class);
        for (PerformanceTiming timing : PerformanceTiming.values()) {
            timingSnapshots.put(timing, TIMINGS.get(timing).snapshot());
        }
        return new PerformanceSnapshot(timingSnapshots, COUNTERS);
    }

    public static void reset() {
        for (RollingTimingWindow timingWindow : TIMINGS.values()) {
            timingWindow.clear();
        }
        for (PerformanceCounter counter : PerformanceCounter.values()) {
            COUNTERS.put(counter, 0L);
        }
    }
}
