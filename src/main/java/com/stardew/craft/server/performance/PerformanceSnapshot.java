package com.stardew.craft.server.performance;

import java.util.Map;

public record PerformanceSnapshot(
    boolean enabled,
    Map<PerformanceTiming, TimingSummary> timings,
    Map<PerformanceCounter, Long> counters,
    DailySettlementMetrics.ReadySummary dailySettlement
) {
    public PerformanceSnapshot(
            Map<PerformanceTiming, TimingSummary> timings,
            Map<PerformanceCounter, Long> counters) {
        this(false, timings, counters, null);
    }

    public PerformanceSnapshot(
            boolean enabled,
            Map<PerformanceTiming, TimingSummary> timings,
            Map<PerformanceCounter, Long> counters) {
        this(enabled, timings, counters, null);
    }

    public PerformanceSnapshot(
            Map<PerformanceTiming, TimingSummary> timings,
            Map<PerformanceCounter, Long> counters,
            DailySettlementMetrics.ReadySummary dailySettlement) {
        this(false, timings, counters, dailySettlement);
    }

    public PerformanceSnapshot {
        timings = Map.copyOf(timings);
        counters = Map.copyOf(counters);
    }
}
