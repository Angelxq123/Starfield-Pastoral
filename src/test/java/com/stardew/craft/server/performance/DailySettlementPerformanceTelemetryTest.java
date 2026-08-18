package com.stardew.craft.server.performance;

import com.stardew.craft.time.settlement.BudgetedWorkRunner;
import com.stardew.craft.time.settlement.DailySettlementContext;
import com.stardew.craft.time.settlement.DailySettlementCoordinator;
import com.stardew.craft.time.settlement.DailySettlementWorkUnit;
import com.stardew.craft.time.settlement.DailySettlementWorkUnits;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DailySettlementPerformanceTelemetryTest {
    private final AtomicLong clock = new AtomicLong();
    private final AtomicLong syncChunkLoads = new AtomicLong();

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
    void enumsExposeEverySettlementTimingAndCounterWithoutRemovingExistingOnes() {
        assertEquals(List.of(
                "SERVER_TICK", "PLAYER_LOGIN_EVENT", "PLAYER_LOGIN_SYNC_STAGE",
                "CONTENT_SNAPSHOT_BUILD",
                "JEI_CATALOG_BUILD", "FARM_SYNC_CHUNK_LOAD", "DAILY_SYNC_CHUNK_LOAD",
                "DAILY_SETTLEMENT_TOTAL", "DAILY_SETTLEMENT_TICK",
                "DAILY_SETTLEMENT_ATOMIC_ITEM", "DAILY_SETTLEMENT_LOCK_TO_READY",
                "NPC_TICK", "FESTIVAL_TICK", "FISHING_TICK", "CUTSCENE_TRIGGER_SCAN",
                "CONTENT_SYNC", "FARM_DAILY_PROCESS", "OFFLINE_FARM_CATCH_UP"),
                java.util.Arrays.stream(PerformanceTiming.values()).map(Enum::name).toList());
        assertEquals(List.of(
                "CONTENT_SYNC_RECIPIENTS", "CONTENT_SYNC_PACKETS", "CONTENT_REGISTRY_BYTES",
                "JEI_CATALOG_ENTRIES", "PLAYER_FULL_SYNC_REQUESTS", "PLAYER_FULL_SYNC_SENT",
                "PLAYER_FULL_SYNC_SKIPPED", "PLAYER_LOGIN_SYNC_ENQUEUED",
                "PLAYER_LOGIN_SYNC_STAGES", "PLAYER_LOGIN_SYNC_COMPLETED",
                "FARM_SYNC_CHUNK_LOADS", "DAILY_SYNC_CHUNK_LOADS",
                "DAILY_SETTLEMENT_TICKS", "DAILY_SETTLEMENT_ITEMS",
                "DAILY_SETTLEMENT_CHUNK_LEASES", "DAILY_SETTLEMENT_OVERSHOOTS",
                "DAILY_SETTLEMENT_RETRIES", "DAILY_SETTLEMENT_PERMANENT_FAILURES",
                "DAILY_SETTLEMENT_PLAYER_BATCHES", "DAILY_SETTLEMENT_READY_PUBLICATIONS",
                "CONTENT_CACHE_HITS", "CONTENT_CACHE_REBUILDS",
                "FARM_CATCH_UP_CHUNKS", "FARM_CATCH_UP_OBJECTS"),
                java.util.Arrays.stream(PerformanceCounter.values()).map(Enum::name).toList());
    }

    @Test
    void readySummaryIsImmutableAndKeepsSubsystemsIndependent() {
        syncChunkLoads.set(11L);
        DailySettlementMetrics metrics = new DailySettlementMetrics(clock::get, syncChunkLoads::get);
        metrics.begin(29);
        clock.set(10L);
        metrics.markLocked();
        clock.set(15L);
        long tickStarted = metrics.beginTick();
        metrics.recordItem("crops", 7L, false, false);
        metrics.recordFailedAttempt("crops", 3L, false);
        metrics.recordItem("animals", 13L, true, true);
        metrics.recordRetry("crops", false);
        metrics.recordRetry("animals", true);
        metrics.recordChunkLeases(4L);
        metrics.recordOvershoot("animals", 5L);
        syncChunkLoads.set(14L);
        clock.set(50L);
        metrics.endTick(tickStarted, 20L);
        clock.set(80L);

        DailySettlementMetrics.ReadySummary summary = metrics.completeReady();

        assertEquals(29, summary.absoluteDay());
        assertEquals(80L, summary.totalWallNanos());
        assertEquals(1L, summary.tickCount());
        assertEquals(20L, summary.maxPerTickWorkNanos());
        assertEquals(4L, summary.leaseCount());
        assertEquals(3L, summary.syncChunkLoadDelta());
        assertEquals(1L, summary.overshootCount());
        assertEquals(5L, summary.worstOvershootNanos());
        assertEquals(1L, summary.playerBatchCount());
        assertEquals(70L, summary.lockToReadyNanos());
        assertEquals(new DailySettlementMetrics.SubsystemMetrics(10L, 1L, 1L, 0L),
                summary.subsystems().get("crops"));
        assertEquals(new DailySettlementMetrics.SubsystemMetrics(13L, 1L, 0L, 1L),
                summary.subsystems().get("animals"));
        assertNotSame(summary.subsystems().get("crops"), summary.subsystems().get("animals"));
        assertThrows(UnsupportedOperationException.class,
                () -> summary.subsystems().put("mail",
                        new DailySettlementMetrics.SubsystemMetrics(0L, 0L, 0L, 0L)));
    }

    @Test
    void metricsPublishIntoRecorderAndFormatterIncludesEveryReadyField() {
        DailySettlementMetrics metrics = new DailySettlementMetrics(clock::get, syncChunkLoads::get);
        metrics.begin(2);
        metrics.markLocked();
        long tick = metrics.beginTick();
        metrics.recordItem("mail", 2_000_000L, true, false);
        metrics.recordChunkLeases(2L);
        metrics.recordOvershoot("mail", 1_000_000L);
        clock.set(4_000_000L);
        metrics.endTick(tick, 2_000_000L);
        DailySettlementMetrics.ReadySummary summary = metrics.completeReady();
        metrics.publishReady(summary);

        PerformanceSnapshot snapshot = ServerPerformanceRecorder.snapshot();
        assertEquals(summary, snapshot.dailySettlement());
        assertEquals(1L, snapshot.counters().get(PerformanceCounter.DAILY_SETTLEMENT_TICKS));
        assertEquals(1L, snapshot.counters().get(PerformanceCounter.DAILY_SETTLEMENT_ITEMS));
        assertEquals(2L, snapshot.counters().get(PerformanceCounter.DAILY_SETTLEMENT_CHUNK_LEASES));
        assertEquals(1L, snapshot.counters().get(PerformanceCounter.DAILY_SETTLEMENT_OVERSHOOTS));
        assertEquals(1L, snapshot.counters().get(PerformanceCounter.DAILY_SETTLEMENT_READY_PUBLICATIONS));

        List<String> lines = PerformanceReportFormatter.format(snapshot);
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("DAILY_SYNC_CHUNK_LOADS=")));
        assertTrue(lines.stream().anyMatch(line -> line.contains(
                "DAILY_SETTLEMENT_READY absoluteDay=2 totalWall=4.000ms ticks=1 maxTickWork=2.000ms")));
        assertTrue(lines.stream().anyMatch(line -> line.contains(
                "leases=2 syncChunkLoads=0 overshoots=1 worstOvershoot=1.000ms")));
        assertTrue(lines.stream().anyMatch(line -> line.contains(
                "playerBatches=0 lockToReady=4.000ms")));
        assertTrue(lines.stream().anyMatch(line -> line.equals(
                "DAILY_SETTLEMENT_SUBSYSTEM name=mail cumulative=2.000ms items=1 retries=0 permanentFailures=0")));
    }

    @Test
    void repeatedOvershootsStayCountedButWarningsAreRateLimitedPerSubsystem()
            throws IOException {
        DailySettlementMetrics metrics = new DailySettlementMetrics(clock::get, syncChunkLoads::get);
        metrics.begin(2);
        metrics.recordOvershoot("farm_debris", 100L);
        metrics.recordOvershoot("farm_debris", 300L);

        DailySettlementMetrics.ReadySummary summary = metrics.completeReady();
        assertEquals(2L, summary.overshootCount());
        assertEquals(300L, summary.worstOvershootNanos());

        String source = source(
                "src/main/java/com/stardew/craft/server/performance/DailySettlementMetrics.java");
        assertTrue(source.contains("warnedOvershootSubsystems.add(subsystemName)"));
    }

    @Test
    void coordinatorChunkScopeAndReadyPublisherUseMetricsOnProductionPaths() throws IOException {
        String coordinator = source(
                "src/main/java/com/stardew/craft/time/settlement/DailySettlementCoordinator.java");
        String chunkManager = source("src/main/java/com/stardew/craft/farm/FarmChunkManager.java");
        String metrics = source(
                "src/main/java/com/stardew/craft/server/performance/DailySettlementMetrics.java");
        String readyPublisher = source(
                "src/main/java/com/stardew/craft/time/settlement/DailySettlementReadyPublisher.java");

        assertTrue(coordinator.contains("DailySettlementMetrics"));
        assertTrue(coordinator.contains("metrics.recordItem"));
        assertTrue(coordinator.contains("metrics.recordRetry"));
        assertTrue(coordinator.contains("metrics.recordOvershoot"));
        assertTrue(chunkManager.contains("recordDailySettlementChunkLeases"));
        assertTrue(chunkManager.contains("measureSynchronousChunkLoad"));
        assertTrue(chunkManager.contains("withinDailyChunkLoadScope"));
        int loadMeasurement = metrics.indexOf("measureSynchronousChunkLoad(Supplier<T> load)");
        int beginMetrics = metrics.indexOf("public void begin(int day)", loadMeasurement);
        String loadMeasurementBody = metrics.substring(loadMeasurement, beginMetrics);
        assertFalse(loadMeasurementBody.contains("coordinator().isActive()"));
        assertTrue(readyPublisher.contains("metrics.publishReady"));
    }

    @Test
    void onlyLoadsInsideTheCallingThreadsDailyLeaseScopeCountAsDaily() throws Exception {
        CountDownLatch dailyScopeEntered = new CountDownLatch(1);
        CountDownLatch outsideLoadFinished = new CountDownLatch(1);
        AtomicInteger dailyResult = new AtomicInteger();
        Thread dailyThread = new Thread(() ->
                DailySettlementMetrics.withinDailyChunkLoadScope(() -> {
                    dailyScopeEntered.countDown();
                    try {
                        assertTrue(outsideLoadFinished.await(10, TimeUnit.SECONDS));
                    } catch (InterruptedException exception) {
                        throw new AssertionError(exception);
                    }
                    dailyResult.set(DailySettlementMetrics.measureSynchronousChunkLoad(() -> 7));
                    return null;
                }));
        dailyThread.start();
        assertTrue(dailyScopeEntered.await(10, TimeUnit.SECONDS));

        assertEquals(3, DailySettlementMetrics.measureSynchronousChunkLoad(() -> 3));
        outsideLoadFinished.countDown();
        dailyThread.join(10_000L);

        assertFalse(dailyThread.isAlive());
        assertEquals(7, dailyResult.get());
        PerformanceSnapshot snapshot = ServerPerformanceRecorder.snapshot();
        assertEquals(2L, snapshot.counters().get(PerformanceCounter.FARM_SYNC_CHUNK_LOADS));
        assertEquals(1L, snapshot.counters().get(PerformanceCounter.DAILY_SYNC_CHUNK_LOADS));
    }

    @Test
    void coordinatorSnapshotsSequenceSubsystemMetadataBeforeTheLastChildCompletes() {
        AtomicLong nanos = new AtomicLong();
        DailySettlementMetrics metrics = new DailySettlementMetrics(
                nanos::incrementAndGet, () -> 0L);
        DailySettlementWorkUnit child = DailySettlementWorkUnits.atomic(
                "inner_atomic", () -> {}, () -> {});
        DailySettlementWorkUnit sequence = DailySettlementWorkUnits.sequence(
                "outer_sequence", List.of(child), () -> {});
        DailySettlementCoordinator coordinator = new DailySettlementCoordinator(
                new BudgetedWorkRunner(() -> nanos.addAndGet(2L)),
                () -> 1L,
                () -> 10,
                (context, builder) -> builder.addPlayer(sequence),
                DailySettlementCoordinator.LifecycleListener.NOOP,
                metrics,
                () -> true);

        assertTrue(coordinator.start(new DailySettlementContext(
                2, 1, 0, 2, 1_560, false, List.of(), Set.of())));
        for (int tick = 0; coordinator.isActive() && tick < 10; tick++) {
            coordinator.tick();
        }

        assertTrue(!coordinator.isActive());
        assertEquals(1L, metrics.readySummary().subsystems()
                .get("inner_atomic").processedItems());
        assertEquals(0L, metrics.readySummary().subsystems()
                .get("inner_atomic").retries());
        assertEquals(1L, metrics.readySummary().overshootCount());
    }

    @Test
    void closeFailureIsReportedAsAPermanentSubsystemFailure() {
        AtomicLong nanos = new AtomicLong();
        DailySettlementMetrics metrics = new DailySettlementMetrics(
                nanos::incrementAndGet, () -> 0L);
        DailySettlementWorkUnit unit = DailySettlementWorkUnits.atomic(
                "close_failure", () -> {},
                () -> { throw new IllegalStateException("close failed"); });
        DailySettlementCoordinator coordinator = new DailySettlementCoordinator(
                new BudgetedWorkRunner(nanos::incrementAndGet),
                () -> 100L,
                () -> 10,
                (context, builder) -> builder.addPrepare(unit),
                DailySettlementCoordinator.LifecycleListener.NOOP,
                metrics,
                () -> true);

        assertTrue(coordinator.start(new DailySettlementContext(
                2, 1, 0, 2, 1_560, false, List.of(), Set.of())));
        for (int tick = 0; coordinator.isActive() && tick < 10; tick++) {
            coordinator.tick();
        }

        assertEquals(1L, metrics.readySummary().subsystems()
                .get("close_failure").permanentFailures());
    }

    @Test
    void sequenceFailureTelemetryUsesTheCurrentChildSubsystemForAllFailures() {
        AtomicLong nanos = new AtomicLong();
        AtomicInteger attempts = new AtomicInteger();
        DailySettlementMetrics metrics = new DailySettlementMetrics(
                nanos::incrementAndGet, () -> 0L);
        DailySettlementWorkUnit child = DailySettlementWorkUnits.atomic(
                "inner_subsystem",
                () -> {
                    attempts.incrementAndGet();
                    throw new IllegalStateException("item failed");
                },
                () -> { throw new IllegalStateException("close failed"); },
                2);
        DailySettlementWorkUnit sequence = DailySettlementWorkUnits.sequence(
                "outer_sequence", List.of(child), () -> {});
        DailySettlementCoordinator coordinator = new DailySettlementCoordinator(
                new BudgetedWorkRunner(nanos::incrementAndGet),
                () -> 100L,
                () -> 10,
                (context, builder) -> builder.addPrepare(sequence),
                DailySettlementCoordinator.LifecycleListener.NOOP,
                metrics,
                () -> true);

        assertTrue(coordinator.start(new DailySettlementContext(
                2, 1, 0, 2, 1_560, false, List.of(), Set.of())));
        for (int tick = 0; coordinator.isActive() && tick < 20; tick++) {
            coordinator.tick();
        }

        DailySettlementMetrics.SubsystemMetrics inner = metrics.readySummary()
                .subsystems().get("inner_subsystem");
        assertEquals(3, attempts.get());
        assertFalse(metrics.readySummary().subsystems().containsKey("outer_sequence"));
        assertEquals(2L, inner.retries());
        assertEquals(2L, inner.permanentFailures());
    }

    @Test
    void totalWallTimeIncludesSuccessfulReadyPublicationWork() {
        AtomicLong nanos = new AtomicLong();
        DailySettlementMetrics metrics = new DailySettlementMetrics(nanos::get, () -> 0L);
        DailySettlementCoordinator coordinator = new DailySettlementCoordinator(
                new BudgetedWorkRunner(nanos::get),
                () -> 100L,
                () -> 10,
                (context, builder) -> {},
                new DailySettlementCoordinator.LifecycleListener() {
                    @Override
                    public void phaseChanged(
                            DailySettlementContext context,
                            com.stardew.craft.time.settlement.DailySettlementPhase phase) {
                    }

                    @Override
                    public void itemFailure(
                            DailySettlementContext context,
                            String unitName,
                            String itemIdentity,
                            int attempt,
                            boolean permanent) {
                    }

                    @Override
                    public void ready(DailySettlementContext context) {
                        nanos.set(50L);
                    }
                },
                metrics,
                () -> true);

        assertTrue(coordinator.start(new DailySettlementContext(
                2, 1, 0, 2, 1_560, false, List.of(), Set.of())));
        coordinator.tick();

        assertEquals(50L, metrics.readySummary().totalWallNanos());
    }

    @Test
    void normalSettlementAndForageProgressLogsAreNotInfoLevel() throws IOException {
        String dimension = source(
                "src/main/java/com/stardew/craft/event/DimensionEventHandler.java");
        String compactDimension = dimension.replaceAll("\\s+", "");
        assertFalse(compactDimension.contains(
                "LOGGER.info(\"Stardewdailysettlementstarted"));
        assertFalse(compactDimension.contains(
                "LOGGER.info(\"Stardewdaypublished"));
        assertTrue(compactDimension.contains(
                "LOGGER.debug(\"Stardewdailysettlementstarted"));
        assertTrue(compactDimension.contains(
                "LOGGER.debug(\"Stardewdaypublished"));
        assertTrue(dimension.contains("Stardew daily settlement started"));
        assertTrue(dimension.contains("Stardew day published"));

        String forage = source(
                "src/main/java/com/stardew/craft/manager/ForageSpawnService.java");
        assertFalse(forage.contains("StardewCraft.LOGGER.info("),
                "successful forage progress must not add per-day or per-zone info logs");
        assertTrue(forage.contains("StardewCraft.LOGGER.debug("));
    }

    private static String source(String relativePath) throws IOException {
        return Files.readString(Path.of(System.getProperty("stardewcraft.projectDir")).resolve(relativePath));
    }
}
