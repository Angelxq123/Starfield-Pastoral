package com.stardew.craft.server.performance;

import com.stardew.craft.Config;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.time.settlement.DailySettlementServices;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import net.minecraft.server.MinecraftServer;

public final class DailySettlementMetrics {
    private static final ThreadLocal<Integer> DAILY_CHUNK_LOAD_SCOPE_DEPTH =
            ThreadLocal.withInitial(() -> 0);
    private final LongSupplier clock;
    private final LongSupplier syncChunkLoads;
    private final Map<String, MutableSubsystemMetrics> subsystems = new LinkedHashMap<>();
    private final Set<String> warnedOvershootSubsystems = new HashSet<>();

    private int absoluteDay = -1;
    private long startedAt;
    private long lockedAt = -1L;
    private long syncChunkLoadsAtStart;
    private long tickCount;
    private long maxPerTickWorkNanos;
    private long leaseCount;
    private long overshootCount;
    private long worstOvershootNanos;
    private long playerBatchCount;
    private ReadySummary readySummary;
    private boolean published;

    public DailySettlementMetrics(LongSupplier clock, LongSupplier syncChunkLoads) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.syncChunkLoads = Objects.requireNonNull(syncChunkLoads, "syncChunkLoads");
    }

    public static DailySettlementMetrics production() {
        return new DailySettlementMetrics(
                System::nanoTime,
                () -> ServerPerformanceRecorder.counterValue(
                        PerformanceCounter.DAILY_SYNC_CHUNK_LOADS));
    }

    public boolean isActive() {
        return absoluteDay > 0;
    }

    public static <T> T withinDailyChunkLoadScope(Supplier<T> operation) {
        Objects.requireNonNull(operation, "operation");
        int previousDepth = DAILY_CHUNK_LOAD_SCOPE_DEPTH.get();
        DAILY_CHUNK_LOAD_SCOPE_DEPTH.set(previousDepth + 1);
        try {
            return operation.get();
        } finally {
            if (previousDepth == 0) {
                DAILY_CHUNK_LOAD_SCOPE_DEPTH.remove();
            } else {
                DAILY_CHUNK_LOAD_SCOPE_DEPTH.set(previousDepth);
            }
        }
    }

    public static <T> T measureSynchronousChunkLoad(Supplier<T> load) {
        Objects.requireNonNull(load, "load");
        ServerPerformanceRecorder.increment(PerformanceCounter.FARM_SYNC_CHUNK_LOADS, 1L);
        if (DAILY_CHUNK_LOAD_SCOPE_DEPTH.get() <= 0) {
            return ServerPerformanceRecorder.measure(
                    PerformanceTiming.FARM_SYNC_CHUNK_LOAD, load);
        }
        ServerPerformanceRecorder.increment(PerformanceCounter.DAILY_SYNC_CHUNK_LOADS, 1L);
        return ServerPerformanceRecorder.measure(
                PerformanceTiming.DAILY_SYNC_CHUNK_LOAD,
                () -> ServerPerformanceRecorder.measure(
                        PerformanceTiming.FARM_SYNC_CHUNK_LOAD, load));
    }

    public void begin(int day) {
        if (day <= 0) {
            throw new IllegalArgumentException("absoluteDay must be positive");
        }
        absoluteDay = day;
        startedAt = clock.getAsLong();
        lockedAt = -1L;
        syncChunkLoadsAtStart = syncChunkLoads.getAsLong();
        tickCount = 0L;
        maxPerTickWorkNanos = 0L;
        leaseCount = 0L;
        overshootCount = 0L;
        worstOvershootNanos = 0L;
        playerBatchCount = 0L;
        readySummary = null;
        published = false;
        subsystems.clear();
        warnedOvershootSubsystems.clear();
    }

    public long beginTick() {
        requireActive();
        tickCount = saturatedAdd(tickCount, 1L);
        ServerPerformanceRecorder.increment(PerformanceCounter.DAILY_SETTLEMENT_TICKS, 1L);
        return clock.getAsLong();
    }

    public void endTick(long tickStartedAt, long workNanos) {
        requireActive();
        long elapsed = elapsedSince(tickStartedAt);
        ServerPerformanceRecorder.record(PerformanceTiming.DAILY_SETTLEMENT_TICK, elapsed);
        maxPerTickWorkNanos = Math.max(maxPerTickWorkNanos, Math.max(0L, workNanos));
    }

    public void recordItem(
            String subsystemName, long nanos, boolean atomic, boolean playerBatch) {
        MutableSubsystemMetrics subsystem = subsystem(subsystemName);
        long safeNanos = Math.max(0L, nanos);
        subsystem.cumulativeNanos = saturatedAdd(subsystem.cumulativeNanos, safeNanos);
        subsystem.processedItems = saturatedAdd(subsystem.processedItems, 1L);
        ServerPerformanceRecorder.increment(PerformanceCounter.DAILY_SETTLEMENT_ITEMS, 1L);
        if (atomic) {
            ServerPerformanceRecorder.record(
                    PerformanceTiming.DAILY_SETTLEMENT_ATOMIC_ITEM, safeNanos);
        }
        if (playerBatch) {
            playerBatchCount = saturatedAdd(playerBatchCount, 1L);
            ServerPerformanceRecorder.increment(
                    PerformanceCounter.DAILY_SETTLEMENT_PLAYER_BATCHES, 1L);
        }
    }

    public void recordFailedAttempt(
            String subsystemName, long nanos, boolean atomic) {
        MutableSubsystemMetrics subsystem = subsystem(subsystemName);
        long safeNanos = Math.max(0L, nanos);
        subsystem.cumulativeNanos = saturatedAdd(subsystem.cumulativeNanos, safeNanos);
        if (atomic) {
            ServerPerformanceRecorder.record(
                    PerformanceTiming.DAILY_SETTLEMENT_ATOMIC_ITEM, safeNanos);
        }
    }

    public void recordRetry(String subsystemName, boolean permanent) {
        recordRetry(subsystemName, "<unknown>", permanent);
    }

    public void recordRetry(
            String subsystemName, String itemIdentity, boolean permanent) {
        MutableSubsystemMetrics subsystem = subsystem(subsystemName);
        if (permanent) {
            subsystem.permanentFailures = saturatedAdd(subsystem.permanentFailures, 1L);
            ServerPerformanceRecorder.increment(
                    PerformanceCounter.DAILY_SETTLEMENT_PERMANENT_FAILURES, 1L);
        } else {
            subsystem.retries = saturatedAdd(subsystem.retries, 1L);
            ServerPerformanceRecorder.increment(
                    PerformanceCounter.DAILY_SETTLEMENT_RETRIES, 1L);
        }
        StardewCraft.LOGGER.warn(
                "[DAILY] settlement {} unit={} item={}",
                permanent ? "permanent failure" : "retry",
                subsystemName,
                itemIdentity);
    }

    public void recordChunkLeases(long count) {
        if (count <= 0L) {
            return;
        }
        requireActive();
        leaseCount = saturatedAdd(leaseCount, count);
        ServerPerformanceRecorder.increment(
                PerformanceCounter.DAILY_SETTLEMENT_CHUNK_LEASES, count);
    }

    public void recordOvershoot(String subsystemName, long overshootNanos) {
        if (overshootNanos <= 0L) {
            return;
        }
        requireActive();
        overshootCount = saturatedAdd(overshootCount, 1L);
        worstOvershootNanos = Math.max(worstOvershootNanos, overshootNanos);
        ServerPerformanceRecorder.increment(
                PerformanceCounter.DAILY_SETTLEMENT_OVERSHOOTS, 1L);
        if (Config.isSettlementDebugLoggingEnabled()
                && warnedOvershootSubsystems.add(subsystemName)) {
            StardewCraft.LOGGER.warn(
                    "[DAILY] settlement budget overshoot unit={} overshoot={}ns "
                            + "(further overshoots are summarized)",
                    subsystemName,
                    overshootNanos);
        }
    }

    public void markLocked() {
        requireActive();
        if (lockedAt < 0L) {
            lockedAt = clock.getAsLong();
        }
    }

    public ReadySummary completeReady() {
        requireActive();
        if (readySummary != null) {
            return readySummary;
        }
        long completedAt = clock.getAsLong();
        long totalWallNanos = Math.max(0L, completedAt - startedAt);
        long lockToReadyNanos = lockedAt < 0L
                ? 0L : Math.max(0L, completedAt - lockedAt);
        long syncChunkLoadDelta = Math.max(
                0L, syncChunkLoads.getAsLong() - syncChunkLoadsAtStart);
        Map<String, SubsystemMetrics> frozenSubsystems = new LinkedHashMap<>();
        subsystems.forEach((name, mutable) -> frozenSubsystems.put(name, mutable.freeze()));
        readySummary = new ReadySummary(
                absoluteDay,
                totalWallNanos,
                tickCount,
                maxPerTickWorkNanos,
                leaseCount,
                syncChunkLoadDelta,
                overshootCount,
                worstOvershootNanos,
                playerBatchCount,
                lockToReadyNanos,
                frozenSubsystems);
        ServerPerformanceRecorder.record(
                PerformanceTiming.DAILY_SETTLEMENT_TOTAL, totalWallNanos);
        ServerPerformanceRecorder.record(
                PerformanceTiming.DAILY_SETTLEMENT_LOCK_TO_READY, lockToReadyNanos);
        return readySummary;
    }

    public ReadySummary readySummary() {
        return readySummary;
    }

    public void publishReady(ReadySummary summary) {
        Objects.requireNonNull(summary, "summary");
        if (summary != readySummary) {
            throw new IllegalArgumentException("summary is not the active READY snapshot");
        }
        if (published) {
            return;
        }
        published = true;
        ServerPerformanceRecorder.publishDailySettlement(summary);
        ServerPerformanceRecorder.increment(
                PerformanceCounter.DAILY_SETTLEMENT_READY_PUBLICATIONS, 1L);
        if (Config.isSettlementDebugLoggingEnabled()) {
            StardewCraft.LOGGER.info(
                    "[DAILY] READY day={} wall={}ns ticks={} maxTick={}ns leases={} "
                            + "syncChunkLoads={} overshoots={} worstOvershoot={}ns "
                            + "playerBatches={} lockToReady={}ns subsystems={}",
                    summary.absoluteDay(),
                    summary.totalWallNanos(),
                    summary.tickCount(),
                    summary.maxPerTickWorkNanos(),
                    summary.leaseCount(),
                    summary.syncChunkLoadDelta(),
                    summary.overshootCount(),
                    summary.worstOvershootNanos(),
                    summary.playerBatchCount(),
                    summary.lockToReadyNanos(),
                    summary.subsystems());
        }
    }

    public void abort() {
        absoluteDay = -1;
        readySummary = null;
        published = false;
        subsystems.clear();
        warnedOvershootSubsystems.clear();
    }

    public static void recordDailySettlementChunkLeases(
            MinecraftServer server, long count) {
        DailySettlementServices.Services services = DailySettlementServices.find(server);
        if (services != null && services.coordinator().isActive()) {
            services.metrics().recordChunkLeases(count);
        }
    }

    private MutableSubsystemMetrics subsystem(String name) {
        requireActive();
        return subsystems.computeIfAbsent(
                Objects.requireNonNull(name, "subsystemName"),
                ignored -> new MutableSubsystemMetrics());
    }

    private void requireActive() {
        if (absoluteDay <= 0) {
            throw new IllegalStateException("No daily settlement metrics are active");
        }
    }

    private long elapsedSince(long started) {
        return Math.max(0L, clock.getAsLong() - started);
    }

    private static long saturatedAdd(long current, long amount) {
        return current > Long.MAX_VALUE - amount ? Long.MAX_VALUE : current + amount;
    }

    private static final class MutableSubsystemMetrics {
        private long cumulativeNanos;
        private long processedItems;
        private long retries;
        private long permanentFailures;

        private SubsystemMetrics freeze() {
            return new SubsystemMetrics(
                    cumulativeNanos, processedItems, retries, permanentFailures);
        }
    }

    public record SubsystemMetrics(
            long cumulativeNanos,
            long processedItems,
            long retries,
            long permanentFailures) {
    }

    public record ReadySummary(
            int absoluteDay,
            long totalWallNanos,
            long tickCount,
            long maxPerTickWorkNanos,
            long leaseCount,
            long syncChunkLoadDelta,
            long overshootCount,
            long worstOvershootNanos,
            long playerBatchCount,
            long lockToReadyNanos,
            Map<String, SubsystemMetrics> subsystems) {
        public ReadySummary {
            subsystems = Map.copyOf(Objects.requireNonNull(subsystems, "subsystems"));
        }
    }
}
