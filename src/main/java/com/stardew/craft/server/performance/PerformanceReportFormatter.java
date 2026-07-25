package com.stardew.craft.server.performance;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public final class PerformanceReportFormatter {
    private PerformanceReportFormatter() {
    }

    public static List<String> format(PerformanceSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        List<String> lines = new ArrayList<>();
        lines.add("Stardew server performance (profiling "
                + (snapshot.enabled() ? "enabled" : "disabled") + ")");

        for (PerformanceTiming timing : PerformanceTiming.values()) {
            TimingSummary summary = snapshot.timings().getOrDefault(timing, TimingSummary.ZERO);
            lines.add(String.format(
                Locale.ROOT,
                "%s samples=%d avg=%.3fms p95=%.3fms p99=%.3fms max=%.3fms",
                timing,
                summary.sampleCount(),
                summary.averageMillis(),
                summary.p95Millis(),
                summary.p99Millis(),
                summary.maxMillis()
            ));
        }

        for (PerformanceCounter counter : PerformanceCounter.values()) {
            lines.add(counter + "=" + snapshot.counters().getOrDefault(counter, 0L));
        }
        DailySettlementMetrics.ReadySummary daily = snapshot.dailySettlement();
        if (daily != null) {
            lines.add(String.format(
                    Locale.ROOT,
                    "DAILY_SETTLEMENT_READY absoluteDay=%d totalWall=%.3fms ticks=%d "
                            + "maxTickWork=%.3fms leases=%d syncChunkLoads=%d overshoots=%d "
                            + "worstOvershoot=%.3fms playerBatches=%d lockToReady=%.3fms",
                    daily.absoluteDay(),
                    nanosToMillis(daily.totalWallNanos()),
                    daily.tickCount(),
                    nanosToMillis(daily.maxPerTickWorkNanos()),
                    daily.leaseCount(),
                    daily.syncChunkLoadDelta(),
                    daily.overshootCount(),
                    nanosToMillis(daily.worstOvershootNanos()),
                    daily.playerBatchCount(),
                    nanosToMillis(daily.lockToReadyNanos())));
            daily.subsystems().entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> {
                        DailySettlementMetrics.SubsystemMetrics subsystem = entry.getValue();
                        lines.add(String.format(
                                Locale.ROOT,
                                "DAILY_SETTLEMENT_SUBSYSTEM name=%s cumulative=%.3fms "
                                        + "items=%d retries=%d permanentFailures=%d",
                                entry.getKey(),
                                nanosToMillis(subsystem.cumulativeNanos()),
                                subsystem.processedItems(),
                                subsystem.retries(),
                                subsystem.permanentFailures()));
                    });
        }
        return List.copyOf(lines);
    }

    private static double nanosToMillis(long nanos) {
        return nanos / 1_000_000.0D;
    }
}
