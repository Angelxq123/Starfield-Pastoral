package com.stardew.craft.time.settlement;

import com.stardew.craft.time.StardewTimeManager;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
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
        assertEquals(DailySettlementPhase.READY, coordinator.phase());
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
        assertEquals(DailySettlementPhase.READY, coordinator.phase());
        assertEquals(new Failure("atomic", "atomic", 3, true), listener.failures.get(2));
    }

    @Test
    void successfulItemResetsConsecutiveFailureCount() {
        PerItemRetryWorkUnit unit = new PerItemRetryWorkUnit();
        RecordingListener listener = new RecordingListener();
        DailySettlementCoordinator coordinator = coordinator(
                ignored -> planWithPrepare(unit), listener, DEFAULT_BUDGET, 10);
        coordinator.start(context());

        while (coordinator.phase() != DailySettlementPhase.READY) {
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

        assertEquals(DailySettlementPhase.READY, coordinator.phase());
        assertEquals(1, successfulCloses.get());
        assertEquals(1, failedCloses.get());
    }

    @Test
    void closeExceptionIsNotReportedAsItemFailureOrRetried() {
        AtomicInteger closes = new AtomicInteger();
        RecordingListener listener = new RecordingListener();
        DailySettlementWorkUnit unit = DailySettlementWorkUnits.atomic(
                "close-failure", () -> {}, () -> {
                    closes.incrementAndGet();
                    throw new IllegalStateException("close failed");
                });
        DailySettlementCoordinator coordinator = coordinator(
                ignored -> planWithPrepare(unit), listener);
        coordinator.start(context());

        assertThrows(IllegalStateException.class, coordinator::tick);

        assertTrue(listener.failures.isEmpty());
        assertEquals(1, closes.get());
        assertEquals(DailySettlementPhase.PREPARE, coordinator.phase());

        coordinator.tick();

        assertEquals(1, closes.get());
        assertEquals(DailySettlementPhase.READY, coordinator.phase());
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
                ignored -> new DailySettlementCoordinator.SettlementPlan(
                        List.of(cursor), List.of(), List.of(), List.of(atomic)), listener);
        coordinator.start(context());

        coordinator.drain();
        coordinator.drain();

        assertEquals(DailySettlementPhase.READY, coordinator.phase());
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
                ignored -> planWithPrepare(budgetUnit), DailySettlementCoordinator.LifecycleListener.NOOP);
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
                ignored -> planWithPrepare(limitUnit), DailySettlementCoordinator.LifecycleListener.NOOP);
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
    void finishReadyReturnsToIdleWhileListenerRetainsReadyContext() {
        RecordingListener listener = new RecordingListener();
        DailySettlementCoordinator coordinator = coordinator(ignored -> emptyPlan(), listener);

        assertThrows(IllegalStateException.class, coordinator::finishReady);
        coordinator.start(context());
        coordinator.drain();
        DailySettlementContext retained = listener.readyContexts.getFirst();

        coordinator.finishReady();

        assertEquals(DailySettlementPhase.IDLE, coordinator.phase());
        assertTrue(coordinator.context().isEmpty());
        assertFalse(coordinator.isActive());
        assertEquals(context(), retained);
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

        assertEquals(DailySettlementPhase.READY, coordinator.phase());
        assertEquals(List.of(
                "phase:PREPARE", "phase:WORLD_BATCHES", "phase:PLAYER_BATCHES",
                "phase:COMMIT", "commit", "close", "phase:READY", "ready"), events);
    }

    @Test
    void failedStartStaysIdleAndClosesPlanUnitsAlreadyCreated() {
        DailySettlementCoordinator createFailure = coordinator(ignored -> {
            throw new IllegalStateException("create failed");
        });

        assertThrows(IllegalStateException.class, () -> createFailure.start(context()));
        assertEquals(DailySettlementPhase.IDLE, createFailure.phase());
        assertTrue(createFailure.context().isEmpty());

        AtomicInteger closes = new AtomicInteger();
        DailySettlementWorkUnit first = DailySettlementWorkUnits.atomic(
                "first", () -> {}, closes::incrementAndGet);
        DailySettlementWorkUnit second = DailySettlementWorkUnits.atomic(
                "second", () -> {}, closes::incrementAndGet);
        DailySettlementCoordinator.LifecycleListener failingListener = new DailySettlementCoordinator.LifecycleListener() {
            @Override
            public void phaseChanged(DailySettlementContext context, DailySettlementPhase phase) {
                throw new IllegalStateException("listener failed");
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
            }
        };
        DailySettlementCoordinator listenerFailure = coordinator(
                ignored -> new DailySettlementCoordinator.SettlementPlan(
                        List.of(first), List.of(), List.of(), List.of(second)), failingListener);

        assertThrows(IllegalStateException.class, () -> listenerFailure.start(context()));
        assertEquals(DailySettlementPhase.IDLE, listenerFailure.phase());
        assertTrue(listenerFailure.context().isEmpty());
        assertEquals(2, closes.get());
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
                        ignored -> emptyPlan(), DailySettlementCoordinator.LifecycleListener.NOOP));
        assertThrows(NullPointerException.class,
                () -> new DailySettlementCoordinator(new BudgetedWorkRunner(() -> 0L), null, () -> 1,
                        ignored -> emptyPlan(), DailySettlementCoordinator.LifecycleListener.NOOP));
        assertThrows(NullPointerException.class,
                () -> new DailySettlementCoordinator(new BudgetedWorkRunner(() -> 0L), () -> 1L, null,
                        ignored -> emptyPlan(), DailySettlementCoordinator.LifecycleListener.NOOP));
        assertThrows(NullPointerException.class,
                () -> new DailySettlementCoordinator(new BudgetedWorkRunner(() -> 0L), () -> 1L, () -> 1,
                        null, DailySettlementCoordinator.LifecycleListener.NOOP));
        assertThrows(NullPointerException.class,
                () -> new DailySettlementCoordinator(new BudgetedWorkRunner(() -> 0L), () -> 1L, () -> 1,
                        ignored -> emptyPlan(), null));

        assertThrows(NullPointerException.class,
                () -> new DailySettlementCoordinator.SettlementPlan(null, List.of(), List.of(), List.of()));
        assertThrows(NullPointerException.class,
                () -> new DailySettlementCoordinator.SettlementPlan(List.of(), null, List.of(), List.of()));
        List<DailySettlementWorkUnit> withNull = new ArrayList<>();
        withNull.add(null);
        assertThrows(NullPointerException.class,
                () -> new DailySettlementCoordinator.SettlementPlan(withNull, List.of(), List.of(), List.of()));
    }

    private static DailySettlementCoordinator coordinator(
            DailySettlementCoordinator.PlanFactory planFactory) {
        return coordinator(planFactory, DailySettlementCoordinator.LifecycleListener.NOOP);
    }

    private static DailySettlementCoordinator coordinator(
            DailySettlementCoordinator.PlanFactory planFactory,
            DailySettlementCoordinator.LifecycleListener listener) {
        return coordinator(planFactory, listener, DEFAULT_BUDGET, DEFAULT_ITEM_LIMIT);
    }

    private static DailySettlementCoordinator coordinator(
            DailySettlementCoordinator.PlanFactory planFactory,
            DailySettlementCoordinator.LifecycleListener listener,
            long budget,
            int itemLimit) {
        return new DailySettlementCoordinator(
                new BudgetedWorkRunner(new StepClock(1L)),
                () -> budget,
                () -> itemLimit,
                planFactory,
                listener);
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
