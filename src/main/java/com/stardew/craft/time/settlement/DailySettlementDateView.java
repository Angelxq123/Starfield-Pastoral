package com.stardew.craft.time.settlement;

import java.util.Objects;
import java.util.Optional;

public final class DailySettlementDateView {
    private static final ThreadLocal<DailySettlementContext> ACTIVE = new ThreadLocal<>();

    private DailySettlementDateView() {
    }

    public static Optional<DailySettlementContext> current() {
        return Optional.ofNullable(ACTIVE.get());
    }

    public static void run(DailySettlementContext context, ThrowingRunnable action) throws Exception {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(action, "action");
        DailySettlementContext active = ACTIVE.get();
        if (active != null) {
            if (active.absoluteDay() != context.absoluteDay()) {
                throw new IllegalStateException(
                        "Settlement date " + active.absoluteDay()
                                + " is already active; cannot nest " + context.absoluteDay());
            }
            action.run();
            return;
        }

        ACTIVE.set(context);
        try {
            action.run();
        } finally {
            ACTIVE.remove();
        }
    }

    @FunctionalInterface
    public interface ThrowingRunnable {
        void run() throws Exception;
    }
}
