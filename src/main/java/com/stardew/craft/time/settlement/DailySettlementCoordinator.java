package com.stardew.craft.time.settlement;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;

public final class DailySettlementCoordinator {
    private static final long STOP_DRAIN_TIMEOUT_NANOS = 2_000_000_000L;
    private static final int STOP_DRAIN_ATTEMPT_LIMIT = 100_000;
    private final BudgetedWorkRunner runner;
    private final LongSupplier budgetNanos;
    private final IntSupplier itemLimit;
    private final PlanFactory planFactory;
    private final LifecycleListener listener;
    private final Set<DailySettlementWorkUnit> closedUnits =
            Collections.newSetFromMap(new IdentityHashMap<>());

    private DailySettlementPhase phase = DailySettlementPhase.IDLE;
    private DailySettlementContext context;
    private SettlementPlan plan;
    private int unitCursor;
    private int consecutiveFailures;
    private boolean readyNotified;

    public DailySettlementCoordinator(
            BudgetedWorkRunner runner,
            LongSupplier budgetNanos,
            IntSupplier itemLimit,
            PlanFactory planFactory,
            LifecycleListener listener) {
        this.runner = Objects.requireNonNull(runner, "runner");
        this.budgetNanos = Objects.requireNonNull(budgetNanos, "budgetNanos");
        this.itemLimit = Objects.requireNonNull(itemLimit, "itemLimit");
        this.planFactory = Objects.requireNonNull(planFactory, "planFactory");
        this.listener = Objects.requireNonNull(listener, "listener");
    }

    public DailySettlementPhase phase() {
        return phase;
    }

    public Optional<DailySettlementContext> context() {
        return Optional.ofNullable(context);
    }

    public boolean isActive() {
        return phase != DailySettlementPhase.IDLE;
    }

    public boolean start(DailySettlementContext newContext) {
        Objects.requireNonNull(newContext, "context");
        if (isActive()) {
            if (context.absoluteDay() == newContext.absoluteDay()) {
                return false;
            }
            throw new IllegalStateException(
                    "Settlement day " + context.absoluteDay()
                            + " is active; cannot start day " + newContext.absoluteDay());
        }

        closedUnits.clear();
        SettlementPlanBuilder builder = new SettlementPlanBuilder();
        SettlementPlan createdPlan;
        try {
            planFactory.build(newContext, builder);
            createdPlan = builder.freeze();
        } catch (RuntimeException | Error failure) {
            builder.seal();
            closeUnits(builder.registeredUnits(), failure);
            resetToIdle();
            throw failure;
        }

        context = newContext;
        plan = createdPlan;
        unitCursor = 0;
        consecutiveFailures = 0;
        readyNotified = false;
        phase = DailySettlementPhase.PREPARE;
        safePhaseChanged();
        return true;
    }

    public void tick() {
        if (phase == DailySettlementPhase.IDLE) {
            return;
        }
        if (phase == DailySettlementPhase.READY) {
            if (notifyReady()) {
                resetToIdle();
            }
            return;
        }

        DailySettlementWorkUnit unit = advanceToWork();
        if (unit == null) {
            return;
        }
        if (unit.isComplete()) {
            completeCurrentUnit(unit);
            return;
        }

        long tickBudget = budgetNanos.getAsLong();
        int tickItemLimit = itemLimit.getAsInt();
        if (tickBudget <= 0L) {
            throw new IllegalArgumentException("budgetNanos must be positive");
        }
        if (tickItemLimit <= 0) {
            throw new IllegalArgumentException("itemLimit must be positive");
        }
        int effectiveItemLimit = consecutiveFailures > 0
                ? Math.min(tickItemLimit, 1)
                : tickItemLimit;
        GuardedWorkUnit guardedUnit = new GuardedWorkUnit(unit, context);
        BudgetedWorkRunner.TickResult result;
        try {
            result = runGuarded(guardedUnit, tickBudget, effectiveItemLimit);
        } catch (WorkItemExecutionException failure) {
            resetFailuresAfterProgress(guardedUnit);
            handleItemFailure(unit);
            return;
        } catch (RuntimeException | Error failure) {
            resetFailuresAfterProgress(guardedUnit);
            throw failure;
        }
        resetFailuresAfterProgress(guardedUnit);
        if (result.complete()) {
            completeCurrentUnit(unit);
        }
    }

    public boolean drain() {
        long now = System.nanoTime();
        long deadline = now > Long.MAX_VALUE - STOP_DRAIN_TIMEOUT_NANOS
                ? Long.MAX_VALUE : now + STOP_DRAIN_TIMEOUT_NANOS;
        return drain(System::nanoTime, deadline, STOP_DRAIN_ATTEMPT_LIMIT);
    }

    boolean drain(int attemptLimit) {
        return drain(() -> 0L, Long.MAX_VALUE, attemptLimit);
    }

    boolean drain(LongSupplier clock, long deadlineNanos, int attemptLimit) {
        Objects.requireNonNull(clock, "clock");
        if (attemptLimit <= 0) {
            throw new IllegalArgumentException("attemptLimit must be positive");
        }
        int attempts = 0;
        while (phase != DailySettlementPhase.IDLE) {
            if (attempts >= attemptLimit || clock.getAsLong() >= deadlineNanos) {
                abortActiveSettlement();
                return false;
            }
            attempts++;
            if (phase == DailySettlementPhase.READY) {
                if (notifyReady()) {
                    resetToIdle();
                }
                continue;
            }
            DailySettlementWorkUnit unit = advanceToWork();
            if (unit == null) {
                continue;
            }
            if (unit.isComplete()) {
                completeCurrentUnit(unit);
                continue;
            }

            try {
                DailySettlementDateView.run(context, unit::runNext);
            } catch (Exception failure) {
                handleItemFailure(unit);
                continue;
            }
            consecutiveFailures = 0;
            if (unit.isComplete()) {
                completeCurrentUnit(unit);
            }
        }
        return true;
    }

    public void finishReady() {
        if (phase != DailySettlementPhase.READY || !readyNotified) {
            throw new IllegalStateException("Settlement is not ready for completion");
        }
        resetToIdle();
    }

    private BudgetedWorkRunner.TickResult runGuarded(
            GuardedWorkUnit unit, long tickBudget, int effectiveItemLimit)
            throws WorkItemExecutionException {
        try {
            return runner.run(unit, tickBudget, effectiveItemLimit);
        } catch (WorkItemExecutionException failure) {
            throw failure;
        } catch (RuntimeException | Error failure) {
            throw failure;
        } catch (Exception failure) {
            throw new IllegalStateException(
                    "Budgeted runner produced an unexpected checked exception", failure);
        }
    }

    private void resetFailuresAfterProgress(GuardedWorkUnit unit) {
        if (unit.successfulRuns() > 0) {
            consecutiveFailures = 0;
        }
    }

    private DailySettlementWorkUnit advanceToWork() {
        while (phase != DailySettlementPhase.IDLE && phase != DailySettlementPhase.READY) {
            List<DailySettlementWorkUnit> units = unitsForCurrentPhase();
            if (unitCursor < units.size()) {
                return units.get(unitCursor);
            }
            enterNextPhase();
        }
        return null;
    }

    private void completeCurrentUnit(DailySettlementWorkUnit unit) {
        unitCursor++;
        consecutiveFailures = 0;
        safeClose(unit);
        advanceToWork();
    }

    private void handleItemFailure(DailySettlementWorkUnit unit) {
        String itemIdentity = unit.currentItemIdentity();
        String unitName = unit.name();
        int maxRetries = unit.maxRetries();
        int attempt = consecutiveFailures + 1;
        boolean permanent = attempt > maxRetries;
        consecutiveFailures = attempt;
        safeItemFailure(unitName, itemIdentity, attempt, permanent);
        if (!permanent) {
            return;
        }

        unit.skipFailedItem();
        consecutiveFailures = 0;
        if (unit.isComplete()) {
            completeCurrentUnit(unit);
        }
    }

    private List<DailySettlementWorkUnit> unitsForCurrentPhase() {
        return switch (phase) {
            case PREPARE -> plan.prepare();
            case WORLD_BATCHES -> plan.world();
            case PLAYER_BATCHES -> plan.players();
            case COMMIT -> plan.commit();
            case IDLE, READY -> List.of();
        };
    }

    private void enterNextPhase() {
        phase = switch (phase) {
            case PREPARE -> DailySettlementPhase.WORLD_BATCHES;
            case WORLD_BATCHES -> DailySettlementPhase.PLAYER_BATCHES;
            case PLAYER_BATCHES -> DailySettlementPhase.COMMIT;
            case COMMIT -> DailySettlementPhase.READY;
            case IDLE, READY -> throw new IllegalStateException(
                    "Cannot advance settlement phase from " + phase);
        };
        unitCursor = 0;
        consecutiveFailures = 0;
        safePhaseChanged();
        if (phase == DailySettlementPhase.READY) {
            if (notifyReady()) {
                resetToIdle();
            }
        }
    }

    private void safePhaseChanged() {
        try {
            listener.phaseChanged(context, phase);
        } catch (RuntimeException | Error ignored) {
        }
    }

    private boolean notifyReady() {
        if (readyNotified) {
            return true;
        }
        try {
            listener.ready(context);
            readyNotified = true;
            return true;
        } catch (RuntimeException | Error ignored) {
            return false;
        }
    }

    private void closeUnit(DailySettlementWorkUnit unit) {
        if (closedUnits.add(unit)) {
            unit.close();
        }
    }

    private void safeClose(DailySettlementWorkUnit unit) {
        try {
            closeUnit(unit);
        } catch (RuntimeException | Error closeFailure) {
            try {
                safeItemFailure(unit.name(), "<close>", 1, true);
            } catch (RuntimeException | Error ignored) {
            }
        }
    }

    private void safeItemFailure(
            String unitName, String itemIdentity, int attempt, boolean permanent) {
        try {
            listener.itemFailure(
                    context, unitName, itemIdentity, attempt, permanent);
        } catch (RuntimeException | Error ignored) {
        }
    }

    private void closeUnits(
            List<DailySettlementWorkUnit> units, Throwable primaryFailure) {
        for (DailySettlementWorkUnit unit : units) {
            try {
                closeUnit(unit);
            } catch (RuntimeException | Error closeFailure) {
                if (closeFailure != primaryFailure) {
                    primaryFailure.addSuppressed(closeFailure);
                }
            }
        }
    }

    private void resetToIdle() {
        phase = DailySettlementPhase.IDLE;
        context = null;
        plan = null;
        unitCursor = 0;
        consecutiveFailures = 0;
        readyNotified = false;
        closedUnits.clear();
    }

    private void abortActiveSettlement() {
        if (plan != null) {
            for (DailySettlementWorkUnit unit : plan.prepare()) {
                safeClose(unit);
            }
            for (DailySettlementWorkUnit unit : plan.world()) {
                safeClose(unit);
            }
            for (DailySettlementWorkUnit unit : plan.players()) {
                safeClose(unit);
            }
            for (DailySettlementWorkUnit unit : plan.commit()) {
                safeClose(unit);
            }
        }
        resetToIdle();
    }

    @FunctionalInterface
    public interface PlanFactory {
        void build(DailySettlementContext context, SettlementPlanBuilder builder);
    }

    public static final class SettlementPlanBuilder {
        private final List<DailySettlementWorkUnit> prepare = new java.util.ArrayList<>();
        private final List<DailySettlementWorkUnit> world = new java.util.ArrayList<>();
        private final List<DailySettlementWorkUnit> players = new java.util.ArrayList<>();
        private final List<DailySettlementWorkUnit> commit = new java.util.ArrayList<>();
        private final List<DailySettlementWorkUnit> registeredUnits = new java.util.ArrayList<>();
        private boolean frozen;

        private SettlementPlanBuilder() {
        }

        public SettlementPlanBuilder addPrepare(DailySettlementWorkUnit unit) {
            return add(prepare, unit);
        }

        public SettlementPlanBuilder addWorld(DailySettlementWorkUnit unit) {
            return add(world, unit);
        }

        public SettlementPlanBuilder addPlayer(DailySettlementWorkUnit unit) {
            return add(players, unit);
        }

        public SettlementPlanBuilder addCommit(DailySettlementWorkUnit unit) {
            return add(commit, unit);
        }

        private SettlementPlanBuilder add(
                List<DailySettlementWorkUnit> phaseUnits,
                DailySettlementWorkUnit unit) {
            if (frozen) {
                throw new IllegalStateException("Settlement plan is already frozen");
            }
            DailySettlementWorkUnit registered = Objects.requireNonNull(unit, "unit");
            phaseUnits.add(registered);
            registeredUnits.add(registered);
            return this;
        }

        private SettlementPlan freeze() {
            seal();
            return new SettlementPlan(prepare, world, players, commit);
        }

        private void seal() {
            frozen = true;
        }

        private List<DailySettlementWorkUnit> registeredUnits() {
            return registeredUnits;
        }
    }

    private static final class GuardedWorkUnit implements DailySettlementWorkUnit {
        private final DailySettlementWorkUnit delegate;
        private final DailySettlementContext context;
        private int successfulRuns;

        private GuardedWorkUnit(
                DailySettlementWorkUnit delegate, DailySettlementContext context) {
            this.delegate = delegate;
            this.context = context;
        }

        @Override
        public String name() {
            return delegate.name();
        }

        @Override
        public String currentItemIdentity() {
            return delegate.currentItemIdentity();
        }

        @Override
        public boolean isComplete() {
            return delegate.isComplete();
        }

        @Override
        public void runNext() throws WorkItemExecutionException {
            try {
                DailySettlementDateView.run(context, delegate::runNext);
                successfulRuns++;
            } catch (Exception failure) {
                throw new WorkItemExecutionException(failure);
            }
        }

        @Override
        public void skipFailedItem() {
            delegate.skipFailedItem();
        }

        @Override
        public int maxRetries() {
            return delegate.maxRetries();
        }

        private int successfulRuns() {
            return successfulRuns;
        }
    }

    private static final class WorkItemExecutionException extends Exception {
        private WorkItemExecutionException(Exception cause) {
            super(cause);
        }
    }

    public record SettlementPlan(
            List<DailySettlementWorkUnit> prepare,
            List<DailySettlementWorkUnit> world,
            List<DailySettlementWorkUnit> players,
            List<DailySettlementWorkUnit> commit) {

        public SettlementPlan {
            prepare = List.copyOf(Objects.requireNonNull(prepare, "prepare"));
            world = List.copyOf(Objects.requireNonNull(world, "world"));
            players = List.copyOf(Objects.requireNonNull(players, "players"));
            commit = List.copyOf(Objects.requireNonNull(commit, "commit"));
        }
    }

    public interface LifecycleListener {
        LifecycleListener NOOP = new LifecycleListener() {
            @Override
            public void phaseChanged(
                    DailySettlementContext context, DailySettlementPhase phase) {
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

        void phaseChanged(DailySettlementContext context, DailySettlementPhase phase);

        void itemFailure(
                DailySettlementContext context,
                String unitName,
                String itemIdentity,
                int attempt,
                boolean permanent);

        void ready(DailySettlementContext context);
    }
}
