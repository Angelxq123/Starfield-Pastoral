package com.stardew.craft.time.settlement;

import java.util.Objects;

public final class BudgetedWorkRunner {
    private final NanoClock clock;

    public BudgetedWorkRunner(NanoClock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public TickResult run(DailySettlementWorkUnit unit, long budgetNanos, int itemLimit)
            throws Exception {
        Objects.requireNonNull(unit, "unit");
        if (budgetNanos <= 0L) {
            throw new IllegalArgumentException("budgetNanos must be positive");
        }
        if (itemLimit <= 0) {
            throw new IllegalArgumentException("itemLimit must be positive");
        }
        if (unit.isComplete()) {
            return new TickResult(0, 0L, 0L, true);
        }

        long startedAt = clock.nanoTime();
        long elapsedNanos = 0L;
        int processedItems = 0;
        boolean stoppedForBudget = false;

        while (!unit.isComplete() && processedItems < itemLimit) {
            if (processedItems > 0) {
                elapsedNanos = elapsedSince(startedAt);
                if (elapsedNanos >= budgetNanos) {
                    stoppedForBudget = true;
                    break;
                }
            }

            unit.runNext();
            processedItems++;
        }

        if (!stoppedForBudget) {
            elapsedNanos = elapsedSince(startedAt);
        }
        long overshootNanos = Math.max(0L, elapsedNanos - budgetNanos);
        return new TickResult(processedItems, elapsedNanos, overshootNanos, unit.isComplete());
    }

    private long elapsedSince(long startedAt) {
        return Math.max(0L, clock.nanoTime() - startedAt);
    }

    @FunctionalInterface
    public interface NanoClock {
        long nanoTime();
    }

    public record TickResult(
            int processedItems,
            long elapsedNanos,
            long overshootNanos,
            boolean complete) {
    }
}
