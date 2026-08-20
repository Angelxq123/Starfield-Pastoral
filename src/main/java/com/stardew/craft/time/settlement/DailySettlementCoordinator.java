package com.stardew.craft.time.settlement;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.server.performance.DailySettlementMetrics;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.IntSupplier;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

public final class DailySettlementCoordinator {
    private static final long STOP_DRAIN_TIMEOUT_NANOS = 2_000_000_000L;
    private static final int STOP_DRAIN_ATTEMPT_LIMIT = 100_000;
    private final BudgetedWorkRunner runner;
    private final LongSupplier budgetNanos;
    private final IntSupplier itemLimit;
    private final PlanFactory planFactory;
    private final LifecycleListener listener;
    private final DailySettlementMetrics metrics;
    private final BooleanSupplier serverThread;
    private final Set<DailySettlementWorkUnit> closedUnits =
            Collections.newSetFromMap(new IdentityHashMap<>());

    private DailySettlementPhase phase = DailySettlementPhase.IDLE;
    private DailySettlementContext context;
    private SettlementPlan plan;
    private int unitCursor;
    private int consecutiveFailures;
    private int readyFailureCount;
    private boolean readyNotified;
    private boolean playerResultsNotified;

    public DailySettlementCoordinator(
            BudgetedWorkRunner runner,
            LongSupplier budgetNanos,
            IntSupplier itemLimit,
            PlanFactory planFactory,
            LifecycleListener listener) {
        this(runner, budgetNanos, itemLimit, planFactory, listener,
                DailySettlementMetrics.production(), () -> true);
    }

    public DailySettlementCoordinator(
            BudgetedWorkRunner runner,
            LongSupplier budgetNanos,
            IntSupplier itemLimit,
            PlanFactory planFactory,
            LifecycleListener listener,
            DailySettlementMetrics metrics,
            BooleanSupplier serverThread) {
        this.runner = Objects.requireNonNull(runner, "runner");
        this.budgetNanos = Objects.requireNonNull(budgetNanos, "budgetNanos");
        this.itemLimit = Objects.requireNonNull(itemLimit, "itemLimit");
        this.planFactory = Objects.requireNonNull(planFactory, "planFactory");
        this.listener = Objects.requireNonNull(listener, "listener");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
        this.serverThread = Objects.requireNonNull(serverThread, "serverThread");
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
        requireServerThread();
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
        metrics.begin(newContext.absoluteDay());
        SettlementPlanBuilder builder = new SettlementPlanBuilder();
        SettlementPlan createdPlan;
        try {
            planFactory.build(newContext, builder);
            createdPlan = builder.freeze();
        } catch (RuntimeException | Error failure) {
            builder.seal();
            closeUnits(builder.registeredUnits(), failure);
            metrics.abort();
            resetToIdle();
            throw failure;
        }

        try {
            planFactory.prepareStart(newContext);
        } catch (RuntimeException | Error failure) {
            closeUnits(builder.registeredUnits(), failure);
            try {
                planFactory.cleanup();
            } catch (RuntimeException | Error cleanupFailure) {
                if (cleanupFailure != failure) {
                    failure.addSuppressed(cleanupFailure);
                }
            }
            metrics.abort();
            resetToIdle();
            throw failure;
        }

        context = newContext;
        plan = createdPlan;
        unitCursor = 0;
        consecutiveFailures = 0;
        readyFailureCount = 0;
        readyNotified = false;
        playerResultsNotified = false;
        phase = DailySettlementPhase.PREPARE;
        safePhaseChanged();
        return true;
    }

    public void tick() {
        requireServerThread();
        if (phase == DailySettlementPhase.IDLE) {
            return;
        }
        long tickStartedAt = metrics.beginTick();
        long tickWorkNanos = 0L;
        try {
            tickWorkNanos = tickActive();
        } finally {
            metrics.endTick(tickStartedAt, tickWorkNanos);
        }
        if (phase == DailySettlementPhase.READY) {
            if (notifyReady()) {
                if (metrics.readySummary() == null) {
                    metrics.completeReady();
                }
                resetToIdle();
            }
        }
    }

    private long tickActive() {
        if (phase == DailySettlementPhase.READY) {
            return 0L;
        }

        long tickBudget = -1L;
        int tickItemLimit = -1;
        long elapsedNanos = 0L;
        int processedItems = 0;

        while (phase != DailySettlementPhase.IDLE
                && phase != DailySettlementPhase.READY) {
            if ((phase == DailySettlementPhase.WORLD_BATCHES
                    || phase == DailySettlementPhase.COMMIT)
                    && !playerResultsNotified) {
                if (!notifyPlayerResults()) {
                    break;
                }
                continue;
            }
            DailySettlementWorkUnit unit = advanceToWork();
            if (unit == null) {
                break;
            }
            if (unit.isComplete()) {
                completeCurrentUnit(unit);
                continue;
            }

            if (tickBudget < 0L) {
                tickBudget = budgetNanos.getAsLong();
                tickItemLimit = itemLimit.getAsInt();
                if (tickBudget <= 0L) {
                    throw new IllegalArgumentException("budgetNanos must be positive");
                }
                if (tickItemLimit <= 0) {
                    throw new IllegalArgumentException("itemLimit must be positive");
                }
            }

            long remainingBudget = tickBudget - elapsedNanos;
            int remainingItems = tickItemLimit - processedItems;
            if (remainingBudget <= 0L || remainingItems <= 0) {
                break;
            }

            int effectiveItemLimit = consecutiveFailures > 0
                    ? Math.min(remainingItems, 1)
                    : remainingItems;
            String budgetSubsystemName = unit.subsystemName();
            GuardedWorkUnit guardedUnit = new GuardedWorkUnit(
                    unit, context, metrics,
                    phase == DailySettlementPhase.PLAYER_BATCHES);
            BudgetedWorkRunner.TickResult result;
            try {
                result = runGuarded(guardedUnit, remainingBudget, effectiveItemLimit);
            } catch (WorkItemExecutionException failure) {
                elapsedNanos += guardedUnit.elapsedNanos();
                processedItems += guardedUnit.successfulRuns();
                resetFailuresAfterProgress(guardedUnit);
                handleItemFailure(unit);
                break;
            } catch (RuntimeException | Error failure) {
                resetFailuresAfterProgress(guardedUnit);
                throw failure;
            }

            elapsedNanos += result.elapsedNanos();
            processedItems += result.processedItems();
            resetFailuresAfterProgress(guardedUnit);
            if (result.complete()) {
                completeCurrentUnit(unit, budgetSubsystemName);
            }
            if (result.overshootNanos() > 0L) {
                metrics.recordOvershoot(budgetSubsystemName, result.overshootNanos());
            }
            if (!result.complete()
                    || elapsedNanos >= tickBudget
                    || processedItems >= tickItemLimit) {
                break;
            }
        }
        return elapsedNanos;
    }

    public boolean drain() {
        requireServerThread();
        long now = System.nanoTime();
        long deadline = now > Long.MAX_VALUE - STOP_DRAIN_TIMEOUT_NANOS
                ? Long.MAX_VALUE : now + STOP_DRAIN_TIMEOUT_NANOS;
        return drain(System::nanoTime, deadline, STOP_DRAIN_ATTEMPT_LIMIT);
    }

    boolean drain(int attemptLimit) {
        requireServerThread();
        return drain(() -> 0L, Long.MAX_VALUE, attemptLimit);
    }

    boolean drain(LongSupplier clock, long deadlineNanos, int attemptLimit) {
        requireServerThread();
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
                    if (metrics.readySummary() == null) {
                        metrics.completeReady();
                    }
                    resetToIdle();
                }
                continue;
            }
            if (phase == DailySettlementPhase.COMMIT && !playerResultsNotified) {
                if (!notifyPlayerResults()) {
                    continue;
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

            String subsystemName = subsystemNameOf(unit);
            try {
                new GuardedWorkUnit(
                        unit, context, metrics,
                        phase == DailySettlementPhase.PLAYER_BATCHES).runNext();
            } catch (Exception failure) {
                handleItemFailure(unit);
                continue;
            }
            consecutiveFailures = 0;
            if (unit.isComplete()) {
                completeCurrentUnit(unit, subsystemName);
            }
        }
        return true;
    }

    public void finishReady() {
        requireServerThread();
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
        completeCurrentUnit(unit, subsystemNameOf(unit));
    }

    private void completeCurrentUnit(DailySettlementWorkUnit unit, String subsystemName) {
        unitCursor++;
        consecutiveFailures = 0;
        safeClose(unit, subsystemName);
        advanceToWork();
    }

    private void handleItemFailure(DailySettlementWorkUnit unit) {
        String itemIdentity = unit.currentItemIdentity();
        String unitName = unit.name();
        String subsystemName = unit.subsystemName();
        int maxRetries = unit.maxRetries();
        int attempt = consecutiveFailures + 1;
        boolean permanent = attempt > maxRetries;
        consecutiveFailures = attempt;
        metrics.recordRetry(subsystemName, itemIdentity, permanent);
        safeItemFailure(unitName, itemIdentity, attempt, permanent);
        if (!permanent) {
            return;
        }

        unit.skipFailedItem();
        consecutiveFailures = 0;
        if (unit.isComplete()) {
            completeCurrentUnit(unit, subsystemName);
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
            case PREPARE -> DailySettlementPhase.PLAYER_BATCHES;
            case PLAYER_BATCHES -> DailySettlementPhase.WORLD_BATCHES;
            case WORLD_BATCHES -> DailySettlementPhase.COMMIT;
            case COMMIT -> DailySettlementPhase.READY;
            case IDLE, READY -> throw new IllegalStateException(
                    "Cannot advance settlement phase from " + phase);
        };
        unitCursor = 0;
        consecutiveFailures = 0;
        safePhaseChanged();
    }

    private boolean notifyPlayerResults() {
        try {
            listener.playerResultsReady(context);
            playerResultsNotified = true;
            return true;
        } catch (RuntimeException | Error failure) {
            if (!playerResultsNotified) {
                StardewCraft.LOGGER.error(
                        "[DAILY] Player settlement publication failed day={}; "
                                + "retrying on next server tick",
                        context == null ? -1 : context.absoluteDay(), failure);
            }
            return false;
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
            if (readyFailureCount > 0) {
                StardewCraft.LOGGER.info(
                        "[DAILY] Settlement READY publication recovered day={} attempts={}",
                        context == null ? -1 : context.absoluteDay(),
                        readyFailureCount + 1);
            }
            return true;
        } catch (RuntimeException | Error failure) {
            readyFailureCount++;
            // READY is retried on the next server tick, but never silently: a failed
            // publisher otherwise leaves clients on the waiting overlay indefinitely.
            if (readyFailureCount == 1 || readyFailureCount % 40 == 0) {
                StardewCraft.LOGGER.error(
                        "[DAILY] Settlement READY publication failed day={} attempt={} "
                                + "phase={}; retrying on next server tick",
                        context == null ? -1 : context.absoluteDay(),
                        readyFailureCount,
                        phase,
                        failure);
            }
            return false;
        }
    }

    private void closeUnit(DailySettlementWorkUnit unit) {
        if (closedUnits.add(unit)) {
            unit.close();
        }
    }

    private void safeClose(DailySettlementWorkUnit unit) {
        safeClose(unit, subsystemNameOf(unit));
    }

    private void safeClose(DailySettlementWorkUnit unit, String subsystemName) {
        try {
            closeUnit(unit);
        } catch (RuntimeException | Error closeFailure) {
            String closeSubsystem = subsystemName;
            try {
                closeSubsystem = unit.closeFailureSubsystemName();
            } catch (RuntimeException ignored) {
            }
            try {
                metrics.recordRetry(closeSubsystem, "<close>", true);
            } catch (RuntimeException | Error ignored) {
            }
            try {
                safeItemFailure(unit.name(), "<close>", 1, true);
            } catch (RuntimeException | Error ignored) {
            }
        }
    }

    private String subsystemNameOf(DailySettlementWorkUnit unit) {
        try {
            return unit.subsystemName();
        } catch (RuntimeException failure) {
            return unit.name();
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
        readyFailureCount = 0;
        readyNotified = false;
        playerResultsNotified = false;
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
        metrics.abort();
        resetToIdle();
    }

    private void requireServerThread() {
        if (!serverThread.getAsBoolean()) {
            throw new IllegalStateException(
                    "Daily settlement coordinator must run on the server tick thread");
        }
    }

    @FunctionalInterface
    public interface PlanFactory {
        void build(DailySettlementContext context, SettlementPlanBuilder builder);

        default void prepareStart(DailySettlementContext context) {
        }

        default void cleanup() {
        }
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
        private final DailySettlementMetrics metrics;
        private final boolean playerBatch;
        private int successfulRuns;
        private long elapsedNanos;

        private GuardedWorkUnit(
                DailySettlementWorkUnit delegate,
                DailySettlementContext context,
                DailySettlementMetrics metrics,
                boolean playerBatch) {
            this.delegate = delegate;
            this.context = context;
            this.metrics = metrics;
            this.playerBatch = playerBatch;
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
            String subsystemName = delegate.subsystemName();
            boolean atomic = delegate.isAtomic();
            long startedAt = System.nanoTime();
            try {
                DailySettlementDateView.run(context, delegate::runNext);
                successfulRuns++;
                long itemNanos = Math.max(0L, System.nanoTime() - startedAt);
                elapsedNanos += itemNanos;
                metrics.recordItem(
                        subsystemName, itemNanos, atomic, playerBatch);
            } catch (Exception failure) {
                long itemNanos = Math.max(0L, System.nanoTime() - startedAt);
                elapsedNanos += itemNanos;
                metrics.recordFailedAttempt(subsystemName, itemNanos, atomic);
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

        @Override
        public String subsystemName() {
            return delegate.subsystemName();
        }

        @Override
        public boolean isAtomic() {
            return delegate.isAtomic();
        }

        private int successfulRuns() {
            return successfulRuns;
        }

        private long elapsedNanos() {
            return elapsedNanos;
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
            public void playerResultsReady(DailySettlementContext context) {
            }

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

        default void playerResultsReady(DailySettlementContext context) {
        }

        void itemFailure(
                DailySettlementContext context,
                String unitName,
                String itemIdentity,
                int attempt,
                boolean permanent);

        void ready(DailySettlementContext context);
    }
}
