package com.stardew.craft.time.settlement;

import com.stardew.craft.server.performance.DailySettlementMetrics;
import com.stardew.craft.time.StardewTimeManager;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DailySettlementCoordinatorTest {
    private static final long DEFAULT_BUDGET = 1_000_000L;
    private static final int DEFAULT_ITEM_LIMIT = 1;

    @Test
    void idleAcceptsOneContext() {
        DailySettlementContext context = context();
        DailySettlementCoordinator coordinator = coordinator(ignored -> emptyPlan());

        assertTrue(coordinator.start(context));

        assertEquals(DailySettlementPhase.PREPARE, coordinator.phase());
        assertEquals(context, coordinator.context().orElseThrow());
        assertTrue(coordinator.isActive());
    }

    @Test
    void startLocksParticipantsBeforeTheFirstTickAndKeepsAllAccessGated() {
        UUID playerId = UUID.randomUUID();
        DailySettlementBarrier barrier = new DailySettlementBarrier();
        DailySettlementAccessGuard accessGuard = new DailySettlementAccessGuard(barrier);
        AtomicReference<DailySettlementCoordinator> coordinatorRef = new AtomicReference<>();
        AtomicBoolean workRan = new AtomicBoolean();
        DailySettlementCoordinator.PlanFactory factory = new DailySettlementCoordinator.PlanFactory() {
            @Override
            public void prepareStart(DailySettlementContext context) {
                assertTrue(coordinatorRef.get().context().isEmpty(),
                        "start lock must run before active context becomes visible");
                barrier.lockAll(context.absoluteDay(), context.playerIds());
                accessGuard.captureAnchor(
                        playerId, net.minecraft.world.level.Level.OVERWORLD,
                        new net.minecraft.world.phys.Vec3(1.0D, 64.0D, 2.0D), 10.0F, 20.0F);
            }

            @Override
                public void build(
                    DailySettlementContext context,
                    DailySettlementCoordinator.SettlementPlanBuilder builder) {
                builder.addPrepare(DailySettlementWorkUnits.atomic(
                        "first", () -> workRan.set(true), () -> {}));
            }
        };
        DailySettlementCoordinator coordinator = new DailySettlementCoordinator(
                new BudgetedWorkRunner(new StepClock(1L)),
                () -> DEFAULT_BUDGET, () -> DEFAULT_ITEM_LIMIT,
                factory, DailySettlementCoordinator.LifecycleListener.NOOP);
        coordinatorRef.set(coordinator);

        DailySettlementContext target = new DailySettlementContext(
                226, 3, 0, 2, 1560, false, List.of(playerId), Set.of());
        assertTrue(coordinator.start(target));
        assertTrue(barrier.isLocked(playerId), "C2S guard must be active immediately after start");
        assertFalse(accessGuard.isGameplayAllowed(playerId));
        assertTrue(accessGuard.rejectTeleport(playerId));
        assertTrue(accessGuard.anchor(playerId).isPresent());
        accessGuard.onLogout(playerId);
        accessGuard.reconnectAnchor(
                playerId, net.minecraft.world.level.Level.NETHER,
                new net.minecraft.world.phys.Vec3(8.0D, 70.0D, 9.0D), 30.0F, 40.0F);
        assertFalse(accessGuard.isGameplayAllowed(playerId));
        assertFalse(workRan.get(), "the first work unit must not establish the first lock");

        coordinator.tick();
        assertTrue(workRan.get());
    }

    @Test
    void failedStartLockCleansUpBeforeCoordinatorBecomesActive() {
        UUID playerId = UUID.randomUUID();
        DailySettlementBarrier barrier = new DailySettlementBarrier();
        DailySettlementCoordinator.PlanFactory factory = new DailySettlementCoordinator.PlanFactory() {
            @Override
            public void prepareStart(DailySettlementContext context) {
                barrier.lockAll(context.absoluteDay(), context.playerIds());
                throw new IllegalStateException("lock failed");
            }

            @Override
            public void cleanup() {
                barrier.clear();
            }

            @Override
            public void build(
                    DailySettlementContext context,
                    DailySettlementCoordinator.SettlementPlanBuilder builder) {
            }
        };
        DailySettlementCoordinator coordinator = new DailySettlementCoordinator(
                new BudgetedWorkRunner(new StepClock(1L)),
                () -> DEFAULT_BUDGET, () -> DEFAULT_ITEM_LIMIT,
                factory, DailySettlementCoordinator.LifecycleListener.NOOP);

        assertThrows(IllegalStateException.class, () -> coordinator.start(
                new DailySettlementContext(
                        226, 3, 0, 2, 1560, false, List.of(playerId), Set.of())));
        assertFalse(coordinator.isActive());
        assertTrue(coordinator.context().isEmpty());
        assertFalse(barrier.isLocked(playerId));
    }

    @Test
    void partialStartNotificationFailureUnlocksEarlierClientsAndCleansServerState() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        DailySettlementBarrier barrier = new DailySettlementBarrier();
        DailySettlementAccessGuard accessGuard = new DailySettlementAccessGuard(barrier);
        List<String> notifications = new ArrayList<>();
        DailySettlementContext target = new DailySettlementContext(
                226, 3, 0, 2, 1560, false, List.of(first, second), Set.of());
        DailySettlementPlanFactory.BarrierNotifier notifier = (playerId, day, locked) -> {
            notifications.add(playerId + ":" + locked);
            if (!locked) {
                assertTrue(barrier.isLocked(playerId),
                        "client rollback should be attempted before server lock cleanup");
                return true;
            }
            accessGuard.captureAnchor(
                    playerId, net.minecraft.world.level.Level.OVERWORLD,
                    new net.minecraft.world.phys.Vec3(1.0D, 64.0D, 2.0D), 0.0F, 0.0F);
            if (playerId.equals(second)) {
                throw new IllegalStateException("second notification failed");
            }
            return true;
        };
        DailySettlementCoordinator.PlanFactory factory = new DailySettlementCoordinator.PlanFactory() {
            @Override
            public void prepareStart(DailySettlementContext context) {
                DailySettlementPlanFactory.lockBarrierAtStart(
                        context, barrier, accessGuard, null, notifier);
            }

            @Override
            public void build(
                    DailySettlementContext context,
                    DailySettlementCoordinator.SettlementPlanBuilder builder) {
            }
        };
        DailySettlementCoordinator coordinator = new DailySettlementCoordinator(
                new BudgetedWorkRunner(new StepClock(1L)),
                () -> DEFAULT_BUDGET, () -> DEFAULT_ITEM_LIMIT,
                factory, DailySettlementCoordinator.LifecycleListener.NOOP);

        IllegalStateException failure = assertThrows(
                IllegalStateException.class, () -> coordinator.start(target));

        assertEquals("second notification failed", failure.getMessage());
        assertEquals(List.of(first + ":true", second + ":true", first + ":false"), notifications);
        assertFalse(coordinator.isActive());
        assertTrue(coordinator.context().isEmpty());
        assertFalse(barrier.isLocked(first));
        assertFalse(barrier.isLocked(second));
        assertTrue(accessGuard.anchor(first).isEmpty());
        assertTrue(accessGuard.anchor(second).isEmpty());
    }

    @Test
    void rollbackNotificationFailureDoesNotBlockRemainingUnlocksOrCleanup() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UUID third = UUID.randomUUID();
        DailySettlementBarrier barrier = new DailySettlementBarrier();
        DailySettlementAccessGuard accessGuard = new DailySettlementAccessGuard(barrier);
        List<String> notifications = new ArrayList<>();
        DailySettlementContext target = new DailySettlementContext(
                226, 3, 0, 2, 1560, false, List.of(first, second, third), Set.of());

        IllegalStateException failure = assertThrows(IllegalStateException.class, () ->
                DailySettlementPlanFactory.lockBarrierAtStart(
                        target, barrier, accessGuard, null, (playerId, day, locked) -> {
                            notifications.add(playerId + ":" + locked);
                            if (locked && playerId.equals(third)) {
                                throw new IllegalStateException("third lock failed");
                            }
                            if (!locked && playerId.equals(first)) {
                                throw new IllegalStateException("first unlock failed");
                            }
                            return true;
                        }));

        assertEquals("third lock failed", failure.getMessage());
        assertEquals(1, failure.getSuppressed().length);
        assertEquals("first unlock failed", failure.getSuppressed()[0].getMessage());
        assertEquals(List.of(
                first + ":true", second + ":true", third + ":true",
                first + ":false", second + ":false"), notifications);
        assertFalse(barrier.isLocked(first));
        assertFalse(barrier.isLocked(second));
        assertFalse(barrier.isLocked(third));
        assertTrue(accessGuard.anchor(first).isEmpty());
        assertTrue(accessGuard.anchor(second).isEmpty());
        assertTrue(accessGuard.anchor(third).isEmpty());
    }

    @Test
    void failedNewStartPreservesAnEarlierUnacknowledgedBarrierState() {
        UUID oldPlayer = UUID.randomUUID();
        UUID newPlayer = UUID.randomUUID();
        DailySettlementBarrier barrier = new DailySettlementBarrier();
        DailySettlementAccessGuard accessGuard = new DailySettlementAccessGuard(barrier);
        DailySettlementBarrier.ReadyResult oldReady =
                new DailySettlementBarrier.ReadyResult(
                        100, new com.stardew.craft.network.overnight.OvernightSettlementPayload(
                                100, List.of(), List.of()));
        barrier.lockAll(100, List.of(oldPlayer));
        assertTrue(barrier.publishReady(oldPlayer, oldReady));
        accessGuard.captureAnchor(
                oldPlayer, net.minecraft.world.level.Level.OVERWORLD,
                new net.minecraft.world.phys.Vec3(1.0D, 64.0D, 2.0D), 0.0F, 0.0F);

        IllegalStateException failure = assertThrows(IllegalStateException.class, () ->
                DailySettlementPlanFactory.lockBarrierAtStart(
                        new DailySettlementContext(
                                227, 3, 0, 3, 1560, false,
                                List.of(newPlayer), Set.of()),
                        barrier, accessGuard, null,
                        (playerId, day, locked) -> {
                            if (locked) {
                                throw new IllegalStateException("new start failed");
                            }
                            return true;
                        }));

        assertEquals("new start failed", failure.getMessage());
        assertTrue(barrier.isLocked(oldPlayer));
        assertEquals(100, barrier.lockedDay(oldPlayer));
        assertSame(oldReady, barrier.readyResult(oldPlayer, 100));
        assertTrue(accessGuard.anchor(oldPlayer).isPresent());
        assertFalse(barrier.isLocked(newPlayer));
    }

    @Test
    void coordinatorFailedStartCleanupPreservesAnEarlierReadyBarrier() {
        UUID oldPlayer = UUID.randomUUID();
        UUID newPlayer = UUID.randomUUID();
        DailySettlementBarrier barrier = new DailySettlementBarrier();
        DailySettlementAccessGuard accessGuard = new DailySettlementAccessGuard(barrier);
        DailySettlementBarrier.ReadyResult oldReady = new DailySettlementBarrier.ReadyResult(
                100, new com.stardew.craft.network.overnight.OvernightSettlementPayload(
                        100, List.of(), List.of()));
        barrier.lockAll(100, List.of(oldPlayer));
        assertTrue(barrier.publishReady(oldPlayer, oldReady));
        accessGuard.captureAnchor(
                oldPlayer, net.minecraft.world.level.Level.OVERWORLD,
                new net.minecraft.world.phys.Vec3(1.0D, 64.0D, 2.0D), 0.0F, 0.0F);
        AtomicInteger dailyProcessCleanups = new AtomicInteger();
        DailySettlementPlanFactory.WorkUnitFactory productionEquivalent =
                new DailySettlementPlanFactory.WorkUnitFactory() {
                    @Override
                    public DailySettlementWorkUnit create(
                            String name, DailySettlementContext context) {
                        return DailySettlementWorkUnits.atomic(name, () -> {}, () -> {});
                    }

                    @Override
                    public void prepareStart(DailySettlementContext context) {
                        DailySettlementPlanFactory.lockBarrierAtStart(
                                context, barrier, accessGuard, null,
                                (playerId, day, locked) -> {
                                    if (locked) {
                                        throw new IllegalStateException("start failed");
                                    }
                                    return true;
                                });
                    }

                    @Override
                    public void cleanup() {
                        dailyProcessCleanups.incrementAndGet();
                    }
                };
        DailySettlementCoordinator coordinator = new DailySettlementCoordinator(
                new BudgetedWorkRunner(() -> 0L), () -> 100L, () -> 10,
                new DailySettlementPlanFactory(productionEquivalent),
                DailySettlementCoordinator.LifecycleListener.NOOP,
                new DailySettlementMetrics(() -> 0L, () -> 0L), () -> true);

        assertThrows(IllegalStateException.class, () -> coordinator.start(
                new DailySettlementContext(
                        227, 3, 0, 3, 1560, false, List.of(newPlayer), Set.of())));

        assertFalse(coordinator.isActive());
        assertEquals(1, dailyProcessCleanups.get());
        assertTrue(barrier.isLocked(oldPlayer));
        assertSame(oldReady, barrier.readyResult(oldPlayer, 100));
        assertTrue(accessGuard.anchor(oldPlayer).isPresent());
        assertFalse(barrier.isLocked(newPlayer));
    }

    @Test
    void productionFailedStartCleanupDoesNotClearBarrierOrAnchors() throws Exception {
        String source = java.nio.file.Files.readString(java.nio.file.Path.of(
                System.getProperty("stardewcraft.projectDir"),
                "src/main/java/com/stardew/craft/time/settlement/DailySettlementPlanFactory.java"));
        int production = source.indexOf("private static final class ProductionWorkUnitFactory");
        int cleanup = source.indexOf("public void cleanup()", production);
        int nextMethod = source.indexOf("private DailySettlementWorkUnit atomic", cleanup);
        String cleanupBody = source.substring(cleanup, nextMethod);

        assertTrue(cleanupBody.contains("cleanupDailyProcess()"));
        assertFalse(cleanupBody.contains("accessGuard.clear()"));
        assertFalse(cleanupBody.contains("barrier.clear()"));
    }

    @Test
    void sequenceCloseFailureUsesTheChildSubsystemThatActuallyFailed() throws Exception {
        AtomicLong nanos = new AtomicLong();
        DailySettlementMetrics metrics = new DailySettlementMetrics(
                nanos::incrementAndGet, () -> 0L);
        DailySettlementWorkUnit first = DailySettlementWorkUnits.atomic(
                "first_child", () -> {}, () -> { throw new IllegalStateException("first close"); });
        first.runNext();
        DailySettlementWorkUnit second = DailySettlementWorkUnits.atomic(
                "second_child", () -> {}, () -> {});
        DailySettlementWorkUnit sequence = DailySettlementWorkUnits.sequence(
                "sequence", List.of(first, second), () -> {});
        DailySettlementCoordinator coordinator = new DailySettlementCoordinator(
                new BudgetedWorkRunner(nanos::incrementAndGet),
                () -> 100L, () -> 10,
                (context, builder) -> builder.addPrepare(sequence),
                DailySettlementCoordinator.LifecycleListener.NOOP,
                metrics,
                () -> true);

        assertTrue(coordinator.start(new DailySettlementContext(
                2, 1, 0, 2, 1560, false, List.of(), Set.of())));
        for (int tick = 0; coordinator.isActive() && tick < 10; tick++) {
            coordinator.tick();
        }

        assertEquals(1L, metrics.readySummary().subsystems()
                .get("first_child").permanentFailures());
        assertEquals(0L, metrics.readySummary().subsystems()
                .get("second_child").permanentFailures());
    }

    @Test
    void nestedSequenceCloseFailureUsesTheLeafSubsystemThatActuallyFailed() throws Exception {
        AtomicLong nanos = new AtomicLong();
        DailySettlementMetrics metrics = new DailySettlementMetrics(
                nanos::incrementAndGet, () -> 0L);
        DailySettlementWorkUnit leaf = DailySettlementWorkUnits.atomic(
                "leaf_child", () -> {},
                () -> { throw new IllegalStateException("leaf close"); });
        leaf.runNext();
        DailySettlementWorkUnit inner = DailySettlementWorkUnits.sequence(
                "inner_sequence", List.of(leaf), () -> {});
        DailySettlementWorkUnit outer = DailySettlementWorkUnits.sequence(
                "outer_sequence", List.of(inner), () -> {});
        DailySettlementCoordinator coordinator = new DailySettlementCoordinator(
                new BudgetedWorkRunner(nanos::incrementAndGet),
                () -> 100L, () -> 10,
                (context, builder) -> builder.addPrepare(outer),
                DailySettlementCoordinator.LifecycleListener.NOOP,
                metrics,
                () -> true);

        assertTrue(coordinator.start(new DailySettlementContext(
                2, 1, 0, 2, 1560, false, List.of(), Set.of())));
        for (int tick = 0; coordinator.isActive() && tick < 10; tick++) {
            coordinator.tick();
        }

        assertEquals(1L, metrics.readySummary().subsystems()
                .get("leaf_child").permanentFailures());
        assertFalse(metrics.readySummary().subsystems().containsKey("inner_sequence"));
        assertFalse(metrics.readySummary().subsystems().containsKey("outer_sequence"));
    }

    @Test
    void duplicateStartForSameDayIsIdempotentAndCreatesPlanOnce() {
        AtomicInteger plans = new AtomicInteger();
        DailySettlementCoordinator coordinator = coordinator(context -> {
            plans.incrementAndGet();
            return emptyPlan();
        });

        assertTrue(coordinator.start(context()));
        assertFalse(coordinator.start(new DailySettlementContext(
                226, 3, 0, 2, 2000, true, List.of(), Set.of())));

        assertEquals(1, plans.get());
    }

    @Test
    void activeSettlementRejectsDifferentDay() {
        DailySettlementCoordinator coordinator = coordinator(ignored -> emptyPlan());
        coordinator.start(context());

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> coordinator.start(new DailySettlementContext(
                        227, 3, 0, 3, 1560, false, List.of(), Set.of())));

        assertTrue(failure.getMessage().contains("226"));
        assertTrue(failure.getMessage().contains("227"));
    }

    @Test
    void notifiesEveryPhaseInStrictOrder() {
        RecordingListener listener = new RecordingListener();
        DailySettlementCoordinator coordinator = coordinator(ignored -> new DailySettlementCoordinator.SettlementPlan(
                List.of(atomic("prepare")),
                List.of(atomic("world")),
                List.of(atomic("players")),
                List.of(atomic("commit"))), listener);

        coordinator.start(context());
        coordinator.tick();
        coordinator.tick();
        coordinator.tick();
        coordinator.tick();

        assertEquals(List.of(
                DailySettlementPhase.PREPARE,
                DailySettlementPhase.WORLD_BATCHES,
                DailySettlementPhase.PLAYER_BATCHES,
                DailySettlementPhase.COMMIT,
                DailySettlementPhase.READY), listener.phases);
        assertEquals(List.of(context()), listener.readyContexts);
    }

    @Test
    void allPlayerWorkUsesOneImmutableTargetDayContext() {
        List<UUID> players = new ArrayList<>(List.of(UUID.randomUUID(), UUID.randomUUID()));
        Set<UUID> farms = new HashSet<>(Set.of(UUID.randomUUID()));
        DailySettlementContext context = new DailySettlementContext(
                226, 3, 0, 2, 1560, false, players, farms);
        AtomicReference<DailySettlementContext> plannedContext = new AtomicReference<>();
        List<DailySettlementContext> workContexts = new ArrayList<>();
        DailySettlementCoordinator coordinator = coordinator(captured -> {
            plannedContext.set(captured);
            List<DailySettlementWorkUnit> playerWork = captured.playerIds().stream()
                    .map(id -> DailySettlementWorkUnits.atomic(
                            "player-" + id, () -> workContexts.add(captured), () -> {}))
                    .toList();
            return new DailySettlementCoordinator.SettlementPlan(
                    List.of(), List.of(), playerWork, List.of());
        });

        coordinator.start(context);
        players.clear();
        farms.clear();
        coordinator.drain();

        assertSame(context, plannedContext.get());
        assertEquals(2, context.playerIds().size());
        assertEquals(1, context.farmOwnerIds().size());
        assertEquals(List.of(context, context), workContexts);
        assertThrows(UnsupportedOperationException.class,
                () -> context.playerIds().add(UUID.randomUUID()));
        assertThrows(UnsupportedOperationException.class,
                () -> context.farmOwnerIds().add(UUID.randomUUID()));
    }

    @Test
    void cursorFailureSkipsImmediatelyAndContinuesOnNextTick() {
        List<String> processed = new ArrayList<>();
        AtomicInteger closes = new AtomicInteger();
        RecordingListener listener = new RecordingListener();
        DailySettlementWorkUnit cursor = DailySettlementWorkUnits.cursor(
                "cursor", List.of("bad", "good"), value -> value, value -> {
                    if (value.equals("bad")) {
                        throw new Exception("failure");
                    }
                    processed.add(value);
                }, closes::incrementAndGet);
        DailySettlementCoordinator coordinator = coordinator(
                ignored -> planWithPrepare(cursor), listener, DEFAULT_BUDGET, 10);

        coordinator.start(context());
        coordinator.tick();

        assertEquals("good", cursor.currentItemIdentity());
        assertEquals(List.of(new Failure("cursor", "bad", 1, true)), listener.failures);
        assertEquals(0, closes.get());

        coordinator.tick();

        assertEquals(List.of("good"), processed);
        assertEquals(1, closes.get());
        assertEquals(DailySettlementPhase.IDLE, coordinator.phase());
    }

    @Test
    void atomicRetriesOnLaterTicksTwiceThenSkipsThirdFailure() {
        AtomicInteger attempts = new AtomicInteger();
        AtomicInteger closes = new AtomicInteger();
        RecordingListener listener = new RecordingListener();
        DailySettlementWorkUnit atomic = DailySettlementWorkUnits.atomic("atomic", () -> {
            attempts.incrementAndGet();
            throw new Exception("failure");
        }, closes::incrementAndGet);
        DailySettlementCoordinator coordinator = coordinator(
                ignored -> planWithPrepare(atomic), listener);
        coordinator.start(context());

        coordinator.tick();
        coordinator.tick();

        assertEquals(DailySettlementPhase.PREPARE, coordinator.phase());
        assertFalse(atomic.isComplete());
        assertEquals(List.of(
                new Failure("atomic", "atomic", 1, false),
                new Failure("atomic", "atomic", 2, false)), listener.failures);

        coordinator.tick();

        assertEquals(3, attempts.get());
        assertTrue(atomic.isComplete());
        assertEquals(1, closes.get());
        assertEquals(DailySettlementPhase.IDLE, coordinator.phase());
        assertEquals(new Failure("atomic", "atomic", 3, true), listener.failures.get(2));
    }

    @Test
    void successfulItemResetsConsecutiveFailureCount() {
        PerItemRetryWorkUnit unit = new PerItemRetryWorkUnit();
        RecordingListener listener = new RecordingListener();
        DailySettlementCoordinator coordinator = coordinator(
                ignored -> planWithPrepare(unit), listener, DEFAULT_BUDGET, 10);
        coordinator.start(context());

        while (coordinator.isActive()) {
            coordinator.tick();
        }

        assertEquals(List.of("A", "B"), unit.processed);
        assertEquals(List.of(
                new Failure("per-item", "A", 1, false),
                new Failure("per-item", "A", 2, false),
                new Failure("per-item", "B", 1, false),
                new Failure("per-item", "B", 2, false)), listener.failures);
    }

    @Test
    void closesUnitsExactlyOnceAfterSuccessAndPermanentFailure() {
        AtomicInteger successfulCloses = new AtomicInteger();
        AtomicInteger failedCloses = new AtomicInteger();
        DailySettlementWorkUnit successful = DailySettlementWorkUnits.atomic(
                "successful", () -> {}, successfulCloses::incrementAndGet);
        DailySettlementWorkUnit failed = DailySettlementWorkUnits.cursor(
                "failed", List.of("bad"), value -> value,
                value -> { throw new Exception("failure"); }, failedCloses::incrementAndGet);
        DailySettlementCoordinator coordinator = coordinator(ignored -> new DailySettlementCoordinator.SettlementPlan(
                List.of(successful), List.of(failed), List.of(), List.of()));

        coordinator.start(context());
        coordinator.tick();
        coordinator.tick();
        coordinator.tick();

        assertEquals(DailySettlementPhase.IDLE, coordinator.phase());
        assertEquals(1, successfulCloses.get());
        assertEquals(1, failedCloses.get());
    }

    @Test
    void drainContinuesThroughCommitWhenAnEarlierCloseFails() {
        AtomicInteger closes = new AtomicInteger();
        List<String> processed = new ArrayList<>();
        RecordingListener listener = new RecordingListener();
        DailySettlementWorkUnit closeFailure = DailySettlementWorkUnits.atomic(
                "close-failure", () -> processed.add("prepare"), () -> {
                    closes.incrementAndGet();
                    throw new IllegalStateException("close failed");
                });
        DailySettlementWorkUnit player = DailySettlementWorkUnits.atomic(
                "player", () -> processed.add("player"), closes::incrementAndGet);
        DailySettlementWorkUnit commit = DailySettlementWorkUnits.atomic(
                "commit", () -> processed.add("commit"), closes::incrementAndGet);
        DailySettlementCoordinator coordinator = coordinator(
                ignored -> new DailySettlementCoordinator.SettlementPlan(
                        List.of(closeFailure), List.of(), List.of(player), List.of(commit)),
                listener);
        coordinator.start(context());

        coordinator.drain();

        assertEquals(List.of("prepare", "player", "commit"), processed);
        assertEquals(3, closes.get());
        assertEquals(DailySettlementPhase.IDLE, coordinator.phase());
        assertEquals(List.of(
                new Failure("close-failure", "<close>", 1, true)), listener.failures);
    }

    @Test
    void drainReachesReadyClosesEverythingOnceAndNeverUsesTickSuppliers() {
        AtomicInteger closes = new AtomicInteger();
        LongSupplier forbiddenBudget = () -> { throw new AssertionError("budget supplier called"); };
        IntSupplier forbiddenLimit = () -> { throw new AssertionError("item limit supplier called"); };
        DailySettlementWorkUnit cursor = DailySettlementWorkUnits.cursor(
                "cursor", List.of("bad", "good"), value -> value,
                value -> { if (value.equals("bad")) throw new Exception("failure"); },
                closes::incrementAndGet);
        DailySettlementWorkUnit atomic = DailySettlementWorkUnits.atomic(
                "atomic", () -> {}, closes::incrementAndGet);
        RecordingListener listener = new RecordingListener();
        DailySettlementCoordinator coordinator = new DailySettlementCoordinator(
                new BudgetedWorkRunner(() -> 0L), forbiddenBudget, forbiddenLimit,
                planFactory(ignored -> new DailySettlementCoordinator.SettlementPlan(
                        List.of(cursor), List.of(), List.of(), List.of(atomic))), listener);
        coordinator.start(context());

        coordinator.drain();
        coordinator.drain();

        assertEquals(DailySettlementPhase.IDLE, coordinator.phase());
        assertEquals(2, closes.get());
        assertEquals(List.of(new Failure("cursor", "bad", 1, true)), listener.failures);
        assertEquals(List.of(context()), listener.readyContexts);
    }

    @Test
    void tickUsesInjectedBudgetAndItemLimit() {
        List<String> budgetProcessed = new ArrayList<>();
        AtomicInteger budgetReads = new AtomicInteger();
        AtomicInteger limitReads = new AtomicInteger();
        DailySettlementWorkUnit budgetUnit = DailySettlementWorkUnits.cursor(
                "budget", List.of("a", "b", "c"), value -> value,
                budgetProcessed::add, () -> {});
        DailySettlementCoordinator budgetCoordinator = new DailySettlementCoordinator(
                new BudgetedWorkRunner(new StepClock(2L)),
                () -> { budgetReads.incrementAndGet(); return 4L; },
                () -> { limitReads.incrementAndGet(); return 10; },
                planFactory(ignored -> planWithPrepare(budgetUnit)),
                DailySettlementCoordinator.LifecycleListener.NOOP);
        budgetCoordinator.start(context());

        budgetCoordinator.tick();

        assertEquals(List.of("a", "b"), budgetProcessed);
        assertEquals(1, budgetReads.get());
        assertEquals(1, limitReads.get());

        List<String> limitProcessed = new ArrayList<>();
        DailySettlementWorkUnit limitUnit = DailySettlementWorkUnits.cursor(
                "limit", List.of("a", "b", "c"), value -> value,
                limitProcessed::add, () -> {});
        DailySettlementCoordinator limitCoordinator = new DailySettlementCoordinator(
                new BudgetedWorkRunner(new StepClock(1L)), () -> 1_000L, () -> 1,
                planFactory(ignored -> planWithPrepare(limitUnit)),
                DailySettlementCoordinator.LifecycleListener.NOOP);
        limitCoordinator.start(context());

        limitCoordinator.tick();

        assertEquals(List.of("a"), limitProcessed);
    }

    @Test
    void invalidTickConfigurationIsNotReportedAsItemFailure() {
        RecordingListener budgetListener = new RecordingListener();
        DailySettlementWorkUnit budgetUnit = DailySettlementWorkUnits.cursor(
                "budget", List.of("a"), value -> value, value -> {}, () -> {});
        DailySettlementCoordinator budgetCoordinator = coordinator(
                ignored -> planWithPrepare(budgetUnit), budgetListener, 0L, 1);
        budgetCoordinator.start(context());

        assertThrows(IllegalArgumentException.class, budgetCoordinator::tick);
        assertTrue(budgetListener.failures.isEmpty());
        assertFalse(budgetUnit.isComplete());

        RecordingListener limitListener = new RecordingListener();
        DailySettlementWorkUnit limitUnit = DailySettlementWorkUnits.cursor(
                "limit", List.of("a"), value -> value, value -> {}, () -> {});
        DailySettlementCoordinator limitCoordinator = coordinator(
                ignored -> planWithPrepare(limitUnit), limitListener, 1L, 0);
        limitCoordinator.start(context());

        assertThrows(IllegalArgumentException.class, limitCoordinator::tick);
        assertTrue(limitListener.failures.isEmpty());
        assertFalse(limitUnit.isComplete());
    }

    @Test
    void clockFailureBeforeItemExecutionDoesNotRetryOrSkipTheItem() {
        RecoverableClock clock = new RecoverableClock();
        AtomicInteger attempts = new AtomicInteger();
        AtomicInteger skips = new AtomicInteger();
        RecordingListener listener = new RecordingListener();
        DailySettlementWorkUnit unit = new DailySettlementWorkUnit() {
            private boolean complete;

            @Override
            public String name() {
                return "clock-sensitive";
            }

            @Override
            public String currentItemIdentity() {
                return "item";
            }

            @Override
            public boolean isComplete() {
                return complete;
            }

            @Override
            public void runNext() throws Exception {
                if (attempts.incrementAndGet() == 1) {
                    throw new Exception("item failed");
                }
                complete = true;
            }

            @Override
            public void skipFailedItem() {
                skips.incrementAndGet();
                complete = true;
            }

            @Override
            public int maxRetries() {
                return 1;
            }
        };
        DailySettlementCoordinator coordinator = new DailySettlementCoordinator(
                new BudgetedWorkRunner(clock),
                () -> DEFAULT_BUDGET,
                () -> DEFAULT_ITEM_LIMIT,
                planFactory(ignored -> planWithPrepare(unit)),
                listener);
        coordinator.start(context());

        assertThrows(IllegalStateException.class, coordinator::tick);

        assertEquals(0, attempts.get());
        assertEquals(0, skips.get());
        assertTrue(listener.failures.isEmpty());
        assertEquals(DailySettlementPhase.PREPARE, coordinator.phase());

        clock.recover();
        coordinator.tick();

        assertEquals(1, attempts.get());
        assertEquals(0, skips.get());
        assertEquals(List.of(
                new Failure("clock-sensitive", "item", 1, false)), listener.failures);
        assertEquals(DailySettlementPhase.PREPARE, coordinator.phase());

        coordinator.tick();

        assertEquals(2, attempts.get());
        assertEquals(0, skips.get());
        assertEquals(DailySettlementPhase.IDLE, coordinator.phase());
    }

    @Test
    void isCompleteFailureIsNotReportedOrSkippedAsAnItemFailure() {
        AtomicInteger processed = new AtomicInteger();
        AtomicInteger skips = new AtomicInteger();
        AtomicReference<Boolean> failCompletionCheck = new AtomicReference<>(true);
        RecordingListener listener = new RecordingListener();
        DailySettlementWorkUnit unit = new DailySettlementWorkUnit() {
            private boolean complete;

            @Override
            public String name() {
                return "completion-sensitive";
            }

            @Override
            public String currentItemIdentity() {
                return "item";
            }

            @Override
            public boolean isComplete() {
                if (failCompletionCheck.get()) {
                    throw new IllegalStateException("completion check failed");
                }
                return complete;
            }

            @Override
            public void runNext() {
                processed.incrementAndGet();
                complete = true;
            }

            @Override
            public void skipFailedItem() {
                skips.incrementAndGet();
                complete = true;
            }
        };
        DailySettlementCoordinator coordinator = coordinator(
                ignored -> planWithPrepare(unit), listener);
        coordinator.start(context());

        assertThrows(IllegalStateException.class, coordinator::tick);

        assertEquals(0, processed.get());
        assertEquals(0, skips.get());
        assertTrue(listener.failures.isEmpty());
        assertEquals(DailySettlementPhase.PREPARE, coordinator.phase());

        failCompletionCheck.set(false);
        coordinator.tick();

        assertEquals(1, processed.get());
        assertEquals(0, skips.get());
        assertEquals(DailySettlementPhase.IDLE, coordinator.phase());
    }

    @Test
    void progressBeforeTailClockFailureResetsRetriesForTheNextItem() {
        FailOnceClock clock = new FailOnceClock(3);
        TwoItemRetryWorkUnit unit = new TwoItemRetryWorkUnit(true, false);
        RecordingListener listener = new RecordingListener();
        DailySettlementCoordinator coordinator = new DailySettlementCoordinator(
                new BudgetedWorkRunner(clock),
                () -> DEFAULT_BUDGET,
                () -> 10,
                planFactory(ignored -> planWithPrepare(unit)),
                listener);
        coordinator.start(context());

        coordinator.tick();
        assertThrows(IllegalStateException.class, coordinator::tick);

        assertEquals("B", unit.currentItemIdentity());
        assertEquals(List.of(
                new Failure("two-item", "A", 1, false)), listener.failures);

        coordinator.tick();

        assertEquals(List.of(
                new Failure("two-item", "A", 1, false),
                new Failure("two-item", "B", 1, false)), listener.failures);
        assertEquals(0, unit.skips());
        assertEquals(DailySettlementPhase.PREPARE, coordinator.phase());

        coordinator.tick();

        assertEquals(2, unit.attemptsFor("B"));
        assertEquals(0, unit.skips());
        assertEquals(DailySettlementPhase.IDLE, coordinator.phase());
    }

    @Test
    void progressBeforeTailCompletionCheckFailureResetsRetriesForTheNextItem() {
        TwoItemRetryWorkUnit unit = new TwoItemRetryWorkUnit(true, true);
        RecordingListener listener = new RecordingListener();
        DailySettlementCoordinator coordinator = coordinator(
                ignored -> planWithPrepare(unit), listener, DEFAULT_BUDGET, 10);
        coordinator.start(context());

        coordinator.tick();
        assertThrows(IllegalStateException.class, coordinator::tick);

        assertTrue(unit.completionCheckFailedAfterProgress());
        assertEquals("B", unit.currentItemIdentity());
        assertEquals(List.of(
                new Failure("two-item", "A", 1, false)), listener.failures);

        coordinator.tick();

        assertEquals(List.of(
                new Failure("two-item", "A", 1, false),
                new Failure("two-item", "B", 1, false)), listener.failures);
        assertEquals(0, unit.skips());
        assertEquals(DailySettlementPhase.PREPARE, coordinator.phase());

        coordinator.tick();

        assertEquals(2, unit.attemptsFor("B"));
        assertEquals(0, unit.skips());
        assertEquals(DailySettlementPhase.IDLE, coordinator.phase());
    }

    @Test
    void failureAfterProgressInOneRunnerInvocationStartsAtAttemptOne() {
        TwoItemRetryWorkUnit unit = new TwoItemRetryWorkUnit(false, false);
        RecordingListener listener = new RecordingListener();
        DailySettlementCoordinator coordinator = coordinator(
                ignored -> planWithPrepare(unit), listener, DEFAULT_BUDGET, 10);
        coordinator.start(context());

        coordinator.tick();

        assertEquals(1, unit.attemptsFor("A"));
        assertEquals(List.of(
                new Failure("two-item", "B", 1, false)), listener.failures);
        assertEquals(0, unit.skips());
        assertEquals(DailySettlementPhase.PREPARE, coordinator.phase());

        coordinator.tick();

        assertEquals(2, unit.attemptsFor("B"));
        assertEquals(DailySettlementPhase.IDLE, coordinator.phase());
    }

    @Test
    void successfulReadyPublicationReturnsToIdleWhileListenerRetainsContext() {
        RecordingListener listener = new RecordingListener();
        DailySettlementCoordinator coordinator = coordinator(ignored -> emptyPlan(), listener);

        assertThrows(IllegalStateException.class, coordinator::finishReady);
        coordinator.start(context());
        coordinator.drain();
        DailySettlementContext retained = listener.readyContexts.getFirst();

        assertEquals(DailySettlementPhase.IDLE, coordinator.phase());
        assertTrue(coordinator.context().isEmpty());
        assertFalse(coordinator.isActive());
        assertEquals(context(), retained);
        assertThrows(IllegalStateException.class, coordinator::finishReady);
    }

    @Test
    void finalCommitCompletionAndReadyCallbackHappenInSameTick() {
        List<String> events = new ArrayList<>();
        DailySettlementWorkUnit commit = DailySettlementWorkUnits.atomic(
                "commit", () -> events.add("commit"), () -> events.add("close"));
        DailySettlementCoordinator.LifecycleListener listener = new DailySettlementCoordinator.LifecycleListener() {
            @Override
            public void phaseChanged(DailySettlementContext context, DailySettlementPhase phase) {
                events.add("phase:" + phase);
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
                events.add("ready");
            }
        };
        DailySettlementCoordinator coordinator = coordinator(
                ignored -> new DailySettlementCoordinator.SettlementPlan(
                        List.of(), List.of(), List.of(), List.of(commit)), listener);
        coordinator.start(context());

        coordinator.tick();

        assertEquals(DailySettlementPhase.IDLE, coordinator.phase());
        assertEquals(List.of(
                "phase:PREPARE", "phase:WORLD_BATCHES", "phase:PLAYER_BATCHES",
                "phase:COMMIT", "commit", "close", "phase:READY", "ready"), events);
    }

    @Test
    void factoryFailureKeepsCoordinatorIdle() {
        DailySettlementCoordinator createFailure = coordinator(ignored -> {
            throw new IllegalStateException("create failed");
        });

        assertThrows(IllegalStateException.class, () -> createFailure.start(context()));
        assertEquals(DailySettlementPhase.IDLE, createFailure.phase());
        assertTrue(createFailure.context().isEmpty());
    }

    @Test
    void itemFailureListenerExceptionDoesNotPreventPermanentSkip() {
        AtomicInteger closes = new AtomicInteger();
        DailySettlementWorkUnit failed = DailySettlementWorkUnits.cursor(
                "failed", List.of("bad"), value -> value,
                value -> { throw new Exception("item failed"); }, closes::incrementAndGet);
        DailySettlementCoordinator.LifecycleListener listener = new DailySettlementCoordinator.LifecycleListener() {
            @Override
            public void phaseChanged(DailySettlementContext context, DailySettlementPhase phase) {
            }

            @Override
            public void itemFailure(
                    DailySettlementContext context,
                    String unitName,
                    String itemIdentity,
                    int attempt,
                    boolean permanent) {
                throw new IllegalStateException("listener failed");
            }

            @Override
            public void ready(DailySettlementContext context) {
            }
        };
        DailySettlementCoordinator coordinator = coordinator(
                ignored -> planWithPrepare(failed), listener);

        coordinator.start(context());
        coordinator.tick();

        assertTrue(failed.isComplete());
        assertEquals(1, closes.get());
        assertEquals(DailySettlementPhase.IDLE, coordinator.phase());
    }

    @Test
    void phaseListenerExceptionsIncludingReadyDoNotBlockReadyCallback() {
        AtomicInteger phaseCalls = new AtomicInteger();
        AtomicInteger readyCalls = new AtomicInteger();
        DailySettlementCoordinator.LifecycleListener listener = new DailySettlementCoordinator.LifecycleListener() {
            @Override
            public void phaseChanged(DailySettlementContext context, DailySettlementPhase phase) {
                phaseCalls.incrementAndGet();
                throw new IllegalStateException("phase listener failed");
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
                readyCalls.incrementAndGet();
            }
        };
        DailySettlementCoordinator coordinator = coordinator(ignored -> emptyPlan(), listener);

        coordinator.start(context());
        coordinator.tick();

        assertEquals(5, phaseCalls.get());
        assertEquals(1, readyCalls.get());
        assertEquals(DailySettlementPhase.IDLE, coordinator.phase());
    }

    @Test
    void readyNotificationRetriesOnLaterTickBeforeFinishIsAllowed() {
        AtomicInteger readyCalls = new AtomicInteger();
        DailySettlementCoordinator.LifecycleListener listener = new DailySettlementCoordinator.LifecycleListener() {
            @Override
            public void phaseChanged(DailySettlementContext context, DailySettlementPhase phase) {
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
                if (readyCalls.incrementAndGet() == 1) {
                    throw new IllegalStateException("ready listener failed");
                }
            }
        };
        DailySettlementCoordinator coordinator = coordinator(ignored -> emptyPlan(), listener);

        coordinator.start(context());
        coordinator.tick();

        assertEquals(DailySettlementPhase.READY, coordinator.phase());
        assertEquals(1, readyCalls.get());
        assertThrows(IllegalStateException.class, coordinator::finishReady);

        coordinator.tick();

        assertEquals(2, readyCalls.get());
        assertEquals(DailySettlementPhase.IDLE, coordinator.phase());
    }

    @Test
    void factoryFailureClosesEveryRegisteredUnitAndSuppressesCloseErrors() {
        AtomicInteger closes = new AtomicInteger();
        DailySettlementWorkUnit closeFailure = DailySettlementWorkUnits.atomic(
                "close-failure", () -> {}, () -> {
                    closes.incrementAndGet();
                    throw new IllegalStateException("close failed");
                });
        DailySettlementWorkUnit normal = DailySettlementWorkUnits.atomic(
                "normal", () -> {}, closes::incrementAndGet);
        DailySettlementCoordinator coordinator = new DailySettlementCoordinator(
                new BudgetedWorkRunner(new StepClock(1L)),
                () -> DEFAULT_BUDGET,
                () -> DEFAULT_ITEM_LIMIT,
                (context, builder) -> {
                    builder.addPrepare(closeFailure);
                    builder.addCommit(normal);
                    throw new IllegalStateException("build failed");
                },
                DailySettlementCoordinator.LifecycleListener.NOOP);

        IllegalStateException failure = assertThrows(
                IllegalStateException.class, () -> coordinator.start(context()));

        assertEquals("build failed", failure.getMessage());
        assertEquals(1, failure.getSuppressed().length);
        assertEquals("close failed", failure.getSuppressed()[0].getMessage());
        assertEquals(2, closes.get());
        assertEquals(DailySettlementPhase.IDLE, coordinator.phase());
        assertTrue(coordinator.context().isEmpty());
    }

    @Test
    void builderRejectsNullUnitsAndFreezesAfterStart() {
        assertBuilderRejectsNull((context, builder) -> builder.addPrepare(null));
        assertBuilderRejectsNull((context, builder) -> builder.addWorld(null));
        assertBuilderRejectsNull((context, builder) -> builder.addPlayer(null));
        assertBuilderRejectsNull((context, builder) -> builder.addCommit(null));

        AtomicReference<DailySettlementCoordinator.SettlementPlanBuilder> captured =
                new AtomicReference<>();
        DailySettlementCoordinator coordinator = new DailySettlementCoordinator(
                new BudgetedWorkRunner(new StepClock(1L)),
                () -> DEFAULT_BUDGET,
                () -> DEFAULT_ITEM_LIMIT,
                (context, builder) -> captured.set(builder),
                DailySettlementCoordinator.LifecycleListener.NOOP);

        coordinator.start(context());

        assertThrows(IllegalStateException.class,
                () -> captured.get().addPrepare(atomic("late")));
    }

    @Test
    void contextValidatesDateAndSnapshotsCollections() {
        List<UUID> players = new ArrayList<>(List.of(UUID.randomUUID()));
        Set<UUID> farms = new HashSet<>(Set.of(UUID.randomUUID()));
        DailySettlementContext context = new DailySettlementContext(
                226, 3, 0, 2, 1560, false, players, farms);
        players.add(UUID.randomUUID());
        farms.add(UUID.randomUUID());

        assertEquals(1, context.playerIds().size());
        assertEquals(1, context.farmOwnerIds().size());
        assertNotSame(players, context.playerIds());
        assertNotSame(farms, context.farmOwnerIds());
        assertThrows(NullPointerException.class,
                () -> new DailySettlementContext(1, 1, 0, 1, 600, false, null, Set.of()));
        assertThrows(NullPointerException.class,
                () -> new DailySettlementContext(1, 1, 0, 1, 600, false, List.of(), null));
        assertThrows(IllegalArgumentException.class,
                () -> new DailySettlementContext(1, 0, 0, 1, 600, false, List.of(), Set.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new DailySettlementContext(1, 1, -1, 1, 600, false, List.of(), Set.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new DailySettlementContext(1, 1, 4, 1, 600, false, List.of(), Set.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new DailySettlementContext(1, 1, 0, 0, 600, false, List.of(), Set.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new DailySettlementContext(1, 1, 0, 29, 600, false, List.of(), Set.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new DailySettlementContext(2, 1, 0, 1, 600, false, List.of(), Set.of()));
    }

    @Test
    void factoryCapturesCurrentAndNextDayWithoutMutatingTime() {
        StardewTimeManager time = timeAt(3, 0, 1, 900);
        List<UUID> players = new ArrayList<>(List.of(UUID.randomUUID()));
        Set<UUID> farms = new HashSet<>(Set.of(UUID.randomUUID()));

        DailySettlementContext next = DailySettlementContextFactory.captureNextDay(
                time, 1560, players, farms);
        DailySettlementContext current = DailySettlementContextFactory.captureCurrentDay(time);
        players.clear();
        farms.clear();

        assertEquals(new DailySettlementContext(
                226, 3, 0, 2, 1560, false, next.playerIds(), next.farmOwnerIds()), next);
        assertEquals(new DailySettlementContext(
                225, 3, 0, 1, 900, false, List.of(), Set.of()), current);
        assertEquals(1, next.playerIds().size());
        assertEquals(1, next.farmOwnerIds().size());
        assertTime(time, 3, 0, 1, 900);
    }

    @Test
    void factoryHandlesSeasonAndYearBoundariesWithoutMutatingTime() {
        StardewTimeManager springEnd = timeAt(2, 0, 28, 1560);
        StardewTimeManager winterEnd = timeAt(2, 3, 28, 1560);

        DailySettlementContext summer = DailySettlementContextFactory.captureNextDay(
                springEnd, 1560, List.of(), List.of());
        DailySettlementContext newYear = DailySettlementContextFactory.captureNextDay(
                winterEnd, 1560, List.of(), List.of());

        assertEquals(new DailySettlementContext(
                141, 2, 1, 1, 1560, true, List.of(), Set.of()), summer);
        assertEquals(new DailySettlementContext(
                225, 3, 0, 1, 1560, true, List.of(), Set.of()), newYear);
        assertTime(springEnd, 2, 0, 28, 1560);
        assertTime(winterEnd, 2, 3, 28, 1560);
    }

    @Test
    void nextDayFactoryRejectsInvalidSourceDatesBeforeRollover() {
        assertThrows(IllegalArgumentException.class,
                () -> DailySettlementContextFactory.captureNextDay(
                        timeAt(0, 0, 1, 600), 1560, List.of(), List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> DailySettlementContextFactory.captureNextDay(
                        timeAt(1, -1, 1, 600), 1560, List.of(), List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> DailySettlementContextFactory.captureNextDay(
                        timeAt(1, 4, 1, 600), 1560, List.of(), List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> DailySettlementContextFactory.captureNextDay(
                        timeAt(1, 0, 0, 600), 1560, List.of(), List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> DailySettlementContextFactory.captureNextDay(
                        timeAt(1, 0, 29, 600), 1560, List.of(), List.of()));
    }

    @Test
    void factoryAndCoordinatorValueObjectsRejectNulls() {
        StardewTimeManager time = timeAt(1, 0, 1, 600);
        assertThrows(NullPointerException.class,
                () -> DailySettlementContextFactory.captureNextDay(null, 600, List.of(), List.of()));
        assertThrows(NullPointerException.class,
                () -> DailySettlementContextFactory.captureNextDay(time, 600, null, List.of()));
        assertThrows(NullPointerException.class,
                () -> DailySettlementContextFactory.captureNextDay(time, 600, List.of(), null));
        assertThrows(NullPointerException.class,
                () -> DailySettlementContextFactory.captureCurrentDay(null));

        assertThrows(NullPointerException.class,
                () -> new DailySettlementCoordinator(null, () -> 1L, () -> 1,
                        planFactory(ignored -> emptyPlan()),
                        DailySettlementCoordinator.LifecycleListener.NOOP));
        assertThrows(NullPointerException.class,
                () -> new DailySettlementCoordinator(new BudgetedWorkRunner(() -> 0L), null, () -> 1,
                        planFactory(ignored -> emptyPlan()),
                        DailySettlementCoordinator.LifecycleListener.NOOP));
        assertThrows(NullPointerException.class,
                () -> new DailySettlementCoordinator(new BudgetedWorkRunner(() -> 0L), () -> 1L, null,
                        planFactory(ignored -> emptyPlan()),
                        DailySettlementCoordinator.LifecycleListener.NOOP));
        assertThrows(NullPointerException.class,
                () -> new DailySettlementCoordinator(new BudgetedWorkRunner(() -> 0L), () -> 1L, () -> 1,
                        null, DailySettlementCoordinator.LifecycleListener.NOOP));
        assertThrows(NullPointerException.class,
                () -> new DailySettlementCoordinator(new BudgetedWorkRunner(() -> 0L), () -> 1L, () -> 1,
                        planFactory(ignored -> emptyPlan()), null));

        assertThrows(NullPointerException.class,
                () -> new DailySettlementCoordinator.SettlementPlan(null, List.of(), List.of(), List.of()));
        assertThrows(NullPointerException.class,
                () -> new DailySettlementCoordinator.SettlementPlan(List.of(), null, List.of(), List.of()));
        List<DailySettlementWorkUnit> withNull = new ArrayList<>();
        withNull.add(null);
        assertThrows(NullPointerException.class,
                () -> new DailySettlementCoordinator.SettlementPlan(withNull, List.of(), List.of(), List.of()));
    }

    @Test
    void boundedDrainAbortsRequiredCleanupAndPublicationFailures() {
        for (String unitName : List.of("daily_process_cleanup", "date_publication")) {
            AtomicInteger attempts = new AtomicInteger();
            AtomicInteger closes = new AtomicInteger();
            DailySettlementWorkUnit failing = new DailySettlementWorkUnit() {
                @Override
                public String name() {
                    return unitName;
                }

                @Override
                public String currentItemIdentity() {
                    return unitName;
                }

                @Override
                public boolean isComplete() {
                    return false;
                }

                @Override
                public void runNext() {
                    attempts.incrementAndGet();
                    throw new IllegalStateException("injected permanent failure");
                }

                @Override
                public void skipFailedItem() {
                    throw new AssertionError("required work must not be skipped");
                }

                @Override
                public int maxRetries() {
                    return Integer.MAX_VALUE;
                }

                @Override
                public void close() {
                    closes.incrementAndGet();
                }
            };
            DailySettlementCoordinator coordinator = coordinator(ignored ->
                    new DailySettlementCoordinator.SettlementPlan(
                            List.of(), List.of(), List.of(), List.of(failing)));
            assertTrue(coordinator.start(context()));

            assertFalse(coordinator.drain(4));

            assertTrue(attempts.get() > 0 && attempts.get() <= 4);
            assertEquals(1, closes.get());
            assertEquals(DailySettlementPhase.IDLE, coordinator.phase());
            assertFalse(coordinator.context().isPresent());
        }
    }

    @Test
    void boundedDrainAbortsPermanentReadyFailure() {
        AtomicInteger readyAttempts = new AtomicInteger();
        DailySettlementCoordinator coordinator = coordinator(
                ignored -> emptyPlan(), new DailySettlementCoordinator.LifecycleListener() {
                    @Override
                    public void phaseChanged(
                            DailySettlementContext context, DailySettlementPhase phase) {
                    }

                    @Override
                    public void itemFailure(
                            DailySettlementContext context, String unitName,
                            String itemIdentity, int attempt, boolean permanent) {
                    }

                    @Override
                    public void ready(DailySettlementContext context) {
                        readyAttempts.incrementAndGet();
                        throw new IllegalStateException("injected READY failure");
                    }
                });
        assertTrue(coordinator.start(context()));

        assertFalse(coordinator.drain(3));

        assertTrue(readyAttempts.get() > 0 && readyAttempts.get() <= 3);
        assertEquals(DailySettlementPhase.IDLE, coordinator.phase());
        assertFalse(coordinator.context().isPresent());
    }

    private static DailySettlementCoordinator coordinator(
            Function<DailySettlementContext, DailySettlementCoordinator.SettlementPlan> planFactory) {
        return coordinator(planFactory, DailySettlementCoordinator.LifecycleListener.NOOP);
    }

    private static DailySettlementCoordinator coordinator(
            Function<DailySettlementContext, DailySettlementCoordinator.SettlementPlan> planFactory,
            DailySettlementCoordinator.LifecycleListener listener) {
        return coordinator(planFactory, listener, DEFAULT_BUDGET, DEFAULT_ITEM_LIMIT);
    }

    private static DailySettlementCoordinator coordinator(
            Function<DailySettlementContext, DailySettlementCoordinator.SettlementPlan> planFactory,
            DailySettlementCoordinator.LifecycleListener listener,
            long budget,
            int itemLimit) {
        return new DailySettlementCoordinator(
                new BudgetedWorkRunner(new StepClock(1L)),
                () -> budget,
                () -> itemLimit,
                planFactory(planFactory),
                listener);
    }

    private static DailySettlementCoordinator.PlanFactory planFactory(
            Function<DailySettlementContext, DailySettlementCoordinator.SettlementPlan> factory) {
        return (context, builder) -> register(builder, factory.apply(context));
    }

    private static void register(
            DailySettlementCoordinator.SettlementPlanBuilder builder,
            DailySettlementCoordinator.SettlementPlan plan) {
        plan.prepare().forEach(builder::addPrepare);
        plan.world().forEach(builder::addWorld);
        plan.players().forEach(builder::addPlayer);
        plan.commit().forEach(builder::addCommit);
    }

    private static void assertBuilderRejectsNull(
            DailySettlementCoordinator.PlanFactory planFactory) {
        DailySettlementCoordinator coordinator = new DailySettlementCoordinator(
                new BudgetedWorkRunner(new StepClock(1L)),
                () -> DEFAULT_BUDGET,
                () -> DEFAULT_ITEM_LIMIT,
                planFactory,
                DailySettlementCoordinator.LifecycleListener.NOOP);

        assertThrows(NullPointerException.class, () -> coordinator.start(context()));
        assertEquals(DailySettlementPhase.IDLE, coordinator.phase());
    }

    private static DailySettlementCoordinator.SettlementPlan emptyPlan() {
        return new DailySettlementCoordinator.SettlementPlan(
                List.of(), List.of(), List.of(), List.of());
    }

    private static DailySettlementCoordinator.SettlementPlan planWithPrepare(
            DailySettlementWorkUnit unit) {
        return new DailySettlementCoordinator.SettlementPlan(
                List.of(unit), List.of(), List.of(), List.of());
    }

    private static DailySettlementWorkUnit atomic(String name) {
        return DailySettlementWorkUnits.atomic(name, () -> {}, () -> {});
    }

    private static DailySettlementContext context() {
        return new DailySettlementContext(
                226, 3, 0, 2, 1560, false, List.of(), Set.of());
    }

    private static StardewTimeManager timeAt(int year, int season, int day, int minute) {
        StardewTimeManager time = new StardewTimeManager();
        time.setCurrentYear(year);
        time.setCurrentSeason(season);
        time.setCurrentDay(day);
        time.setCurrentTime(minute);
        return time;
    }

    private static void assertTime(
            StardewTimeManager time, int year, int season, int day, int minute) {
        assertEquals(year, time.getCurrentYear());
        assertEquals(season, time.getCurrentSeason());
        assertEquals(day, time.getCurrentDay());
        assertEquals(minute, time.getCurrentTime());
    }

    private record Failure(
            String unitName,
            String itemIdentity,
            int attempt,
            boolean permanent) {
    }

    private static final class RecordingListener implements DailySettlementCoordinator.LifecycleListener {
        private final List<DailySettlementPhase> phases = new ArrayList<>();
        private final List<Failure> failures = new ArrayList<>();
        private final List<DailySettlementContext> readyContexts = new ArrayList<>();

        @Override
        public void phaseChanged(DailySettlementContext context, DailySettlementPhase phase) {
            assertEquals(226, context.absoluteDay());
            phases.add(phase);
        }

        @Override
        public void itemFailure(
                DailySettlementContext context,
                String unitName,
                String itemIdentity,
                int attempt,
                boolean permanent) {
            assertEquals(226, context.absoluteDay());
            failures.add(new Failure(unitName, itemIdentity, attempt, permanent));
        }

        @Override
        public void ready(DailySettlementContext context) {
            readyContexts.add(context);
        }
    }

    private static final class StepClock implements BudgetedWorkRunner.NanoClock {
        private final long step;
        private long now;

        private StepClock(long step) {
            this.step = step;
        }

        @Override
        public long nanoTime() {
            now += step;
            return now;
        }
    }

    private static final class RecoverableClock implements BudgetedWorkRunner.NanoClock {
        private boolean failed = true;
        private long now;

        @Override
        public long nanoTime() {
            if (failed) {
                throw new IllegalStateException("clock failed");
            }
            return ++now;
        }

        private void recover() {
            failed = false;
        }
    }

    private static final class FailOnceClock implements BudgetedWorkRunner.NanoClock {
        private final int failingCall;
        private int calls;

        private FailOnceClock(int failingCall) {
            this.failingCall = failingCall;
        }

        @Override
        public long nanoTime() {
            if (++calls == failingCall) {
                throw new IllegalStateException("clock failed after progress");
            }
            return calls;
        }
    }

    private static final class TwoItemRetryWorkUnit implements DailySettlementWorkUnit {
        private final List<String> items = List.of("A", "B");
        private final int[] attempts = new int[2];
        private final boolean failFirstA;
        private final boolean failCompletionCheckAfterA;
        private int cursor;
        private int skips;
        private boolean completionFailurePending;
        private boolean completionCheckFailedAfterProgress;

        private TwoItemRetryWorkUnit(
                boolean failFirstA, boolean failCompletionCheckAfterA) {
            this.failFirstA = failFirstA;
            this.failCompletionCheckAfterA = failCompletionCheckAfterA;
        }

        @Override
        public String name() {
            return "two-item";
        }

        @Override
        public String currentItemIdentity() {
            return items.get(cursor);
        }

        @Override
        public boolean isComplete() {
            if (completionFailurePending) {
                completionFailurePending = false;
                completionCheckFailedAfterProgress = true;
                throw new IllegalStateException("completion check failed after progress");
            }
            return cursor >= items.size();
        }

        @Override
        public void runNext() throws Exception {
            int item = cursor;
            int attempt = ++attempts[item];
            if ((item == 0 && failFirstA && attempt == 1)
                    || (item == 1 && attempt == 1)) {
                throw new Exception("item failed");
            }
            cursor++;
            if (cursor == 1 && failCompletionCheckAfterA) {
                completionFailurePending = true;
            }
        }

        @Override
        public void skipFailedItem() {
            skips++;
            cursor++;
            completionFailurePending = false;
        }

        @Override
        public int maxRetries() {
            return 1;
        }

        private int attemptsFor(String item) {
            return attempts[items.indexOf(item)];
        }

        private int skips() {
            return skips;
        }

        private boolean completionCheckFailedAfterProgress() {
            return completionCheckFailedAfterProgress;
        }
    }

    private static final class PerItemRetryWorkUnit implements DailySettlementWorkUnit {
        private final List<String> items = List.of("A", "B");
        private final int[] attempts = new int[2];
        private final List<String> processed = new ArrayList<>();
        private int cursor;

        @Override
        public String name() {
            return "per-item";
        }

        @Override
        public String currentItemIdentity() {
            return items.get(cursor);
        }

        @Override
        public boolean isComplete() {
            return cursor >= items.size();
        }

        @Override
        public void runNext() throws Exception {
            if (++attempts[cursor] <= 2) {
                throw new Exception("retry");
            }
            processed.add(items.get(cursor));
            cursor++;
        }

        @Override
        public void skipFailedItem() {
            cursor++;
        }

        @Override
        public int maxRetries() {
            return 2;
        }
    }
}
