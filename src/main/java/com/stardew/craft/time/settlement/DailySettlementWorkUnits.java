package com.stardew.craft.time.settlement;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public final class DailySettlementWorkUnits {
    private DailySettlementWorkUnits() {
    }

    public static <T> DailySettlementWorkUnit cursor(
            String name,
            Collection<? extends T> entries,
            Function<? super T, String> identity,
            ThrowingConsumer<? super T> consumer,
            Runnable onClose) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(entries, "entries");
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(consumer, "consumer");
        Objects.requireNonNull(onClose, "onClose");

        List<T> snapshot = List.copyOf(entries);
        List<String> identities = new ArrayList<>(snapshot.size());
        for (T entry : snapshot) {
            identities.add(Objects.requireNonNull(identity.apply(entry), "item identity"));
        }
        return new CursorWorkUnit<>(
                name, snapshot, List.copyOf(identities), consumer, onClose);
    }

    public static DailySettlementWorkUnit atomic(
            String name,
            ThrowingRunnable action,
            Runnable onClose) {
        return new AtomicWorkUnit(
                Objects.requireNonNull(name, "name"),
                Objects.requireNonNull(action, "action"),
                Objects.requireNonNull(onClose, "onClose"));
    }

    public static void drain(DailySettlementWorkUnit unit) {
        Objects.requireNonNull(unit, "unit");
        try (unit) {
            int consecutiveFailures = 0;
            while (!unit.isComplete()) {
                try {
                    unit.runNext();
                    consecutiveFailures = 0;
                } catch (Exception ignored) {
                    consecutiveFailures++;
                    if (consecutiveFailures > unit.maxRetries()) {
                        unit.skipFailedItem();
                        consecutiveFailures = 0;
                    }
                }
            }
        }
    }

    @FunctionalInterface
    public interface ThrowingConsumer<T> {
        void accept(T value) throws Exception;
    }

    @FunctionalInterface
    public interface ThrowingRunnable {
        void run() throws Exception;
    }

    private static final class CursorWorkUnit<T> implements DailySettlementWorkUnit {
        private final String name;
        private final List<T> entries;
        private final List<String> identities;
        private final ThrowingConsumer<? super T> consumer;
        private final Runnable onClose;
        private int cursor;
        private boolean closed;

        private CursorWorkUnit(
                String name,
                List<T> entries,
                List<String> identities,
                ThrowingConsumer<? super T> consumer,
                Runnable onClose) {
            this.name = name;
            this.entries = entries;
            this.identities = identities;
            this.consumer = consumer;
            this.onClose = onClose;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public String currentItemIdentity() {
            return isComplete() ? name : identities.get(cursor);
        }

        @Override
        public boolean isComplete() {
            return cursor >= entries.size();
        }

        @Override
        public void runNext() throws Exception {
            requireCurrentItem();
            consumer.accept(entries.get(cursor));
            cursor++;
        }

        @Override
        public void skipFailedItem() {
            requireCurrentItem();
            cursor++;
        }

        @Override
        public synchronized void close() {
            if (closed) {
                return;
            }
            closed = true;
            onClose.run();
        }

        private void requireCurrentItem() {
            if (isComplete()) {
                throw new IllegalStateException("Work unit is already complete: " + name);
            }
        }
    }

    private static final class AtomicWorkUnit implements DailySettlementWorkUnit {
        private final String name;
        private final ThrowingRunnable action;
        private final Runnable onClose;
        private boolean complete;
        private boolean closed;

        private AtomicWorkUnit(String name, ThrowingRunnable action, Runnable onClose) {
            this.name = name;
            this.action = action;
            this.onClose = onClose;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public String currentItemIdentity() {
            return name;
        }

        @Override
        public boolean isComplete() {
            return complete;
        }

        @Override
        public void runNext() throws Exception {
            if (complete) {
                throw new IllegalStateException("Work unit is already complete: " + name);
            }
            action.run();
            complete = true;
        }

        @Override
        public void skipFailedItem() {
            complete = true;
        }

        @Override
        public int maxRetries() {
            return 2;
        }

        @Override
        public synchronized void close() {
            if (closed) {
                return;
            }
            closed = true;
            onClose.run();
        }
    }
}
