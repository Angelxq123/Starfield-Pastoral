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

        SettlementPlan createdPlan = null;
        try {
            createdPlan = Objects.requireNonNull(
                    planFactory.create(newContext), "planFactory result");
            context = newContext;
            plan = createdPlan;
            unitCursor = 0;
            consecutiveFailures = 0;
            closedUnits.clear();
            phase = DailySettlementPhase.PREPARE;
            listener.phaseChanged(context, phase);
            return true;
        } catch (RuntimeException | Error failure) {
            if (createdPlan != null) {
                closePlanUnits(createdPlan, failure);
            }
            resetToIdle();
            throw failure;
        }
    }

    public void tick() {
        if (phase == DailySettlementPhase.IDLE || phase == DailySettlementPhase.READY) {
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
        BudgetedWorkRunner.TickResult result;
        try {
            result = runner.run(unit, tickBudget, effectiveItemLimit);
        } catch (Exception failure) {
            handleItemFailure(unit);
            return;
        }
        if (result.processedItems() > 0) {
            consecutiveFailures = 0;
        }
        if (result.complete()) {
            completeCurrentUnit(unit);
        }
    }

    public void drain() {
        while (phase != DailySettlementPhase.IDLE && phase != DailySettlementPhase.READY) {
            DailySettlementWorkUnit unit = advanceToWork();
            if (unit == null) {
                continue;
            }
            if (unit.isComplete()) {
                completeCurrentUnit(unit);
                continue;
            }

            try {
                unit.runNext();
            } catch (Exception failure) {
                handleItemFailure(unit);
                continue;
            }
            consecutiveFailures = 0;
            if (unit.isComplete()) {
                completeCurrentUnit(unit);
            }
        }
    }

    public void finishReady() {
        if (phase != DailySettlementPhase.READY) {
            throw new IllegalStateException("Settlement is not ready");
        }
        resetToIdle();
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
        closeUnit(unit);
        unitCursor++;
        consecutiveFailures = 0;
        advanceToWork();
    }

    private void handleItemFailure(DailySettlementWorkUnit unit) {
        String itemIdentity = unit.currentItemIdentity();
        int attempt = ++consecutiveFailures;
        boolean permanent = attempt > unit.maxRetries();
        listener.itemFailure(
                context, unit.name(), itemIdentity, attempt, permanent);
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
        listener.phaseChanged(context, phase);
        if (phase == DailySettlementPhase.READY) {
            listener.ready(context);
        }
    }

    private void closeUnit(DailySettlementWorkUnit unit) {
        if (closedUnits.add(unit)) {
            unit.close();
        }
    }

    private void closePlanUnits(SettlementPlan settlementPlan, Throwable primaryFailure) {
        for (DailySettlementWorkUnit unit : settlementPlan.allUnits()) {
            try {
                closeUnit(unit);
            } catch (RuntimeException | Error closeFailure) {
                primaryFailure.addSuppressed(closeFailure);
            }
        }
    }

    private void resetToIdle() {
        phase = DailySettlementPhase.IDLE;
        context = null;
        plan = null;
        unitCursor = 0;
        consecutiveFailures = 0;
        closedUnits.clear();
    }

    @FunctionalInterface
    public interface PlanFactory {
        SettlementPlan create(DailySettlementContext context);
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

        private List<DailySettlementWorkUnit> allUnits() {
            return java.util.stream.Stream.of(prepare, world, players, commit)
                    .flatMap(List::stream)
                    .toList();
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
