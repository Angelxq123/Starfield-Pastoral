package com.stardew.craft.time.settlement;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

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
        return atomic(name, action, onClose, 2);
    }

    public static DailySettlementWorkUnit atomic(
            String name,
            ThrowingRunnable action,
            Runnable onClose,
            int maxRetries) {
        if (maxRetries < 0) {
            throw new IllegalArgumentException("maxRetries must not be negative");
        }
        return new AtomicWorkUnit(
                Objects.requireNonNull(name, "name"),
                Objects.requireNonNull(action, "action"),
                Objects.requireNonNull(onClose, "onClose"),
                maxRetries);
    }

    public static DailySettlementWorkUnit sequence(
            String name,
            Collection<? extends DailySettlementWorkUnit> children,
            Runnable onClose) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(children, "children");
        Objects.requireNonNull(onClose, "onClose");
        return new SequenceWorkUnit(name, List.copyOf(children), onClose);
    }

    public static DailySettlementWorkUnit deferred(
            String name,
            Supplier<? extends DailySettlementWorkUnit> supplier) {
        return new DeferredWorkUnit(
                Objects.requireNonNull(name, "name"),
                Objects.requireNonNull(supplier, "supplier"));
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
        private final int maxRetries;
        private boolean complete;
        private boolean closed;

        private AtomicWorkUnit(
                String name, ThrowingRunnable action, Runnable onClose, int maxRetries) {
            this.name = name;
            this.action = action;
            this.onClose = onClose;
            this.maxRetries = maxRetries;
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
            return maxRetries;
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

    private static final class SequenceWorkUnit implements DailySettlementWorkUnit {
        private final String name;
        private final List<DailySettlementWorkUnit> children;
        private final boolean[] childClosed;
        private final Runnable onClose;
        private int cursor;
        private boolean closed;
        private Throwable closeFailure;

        private SequenceWorkUnit(
                String name,
                List<DailySettlementWorkUnit> children,
                Runnable onClose) {
            this.name = name;
            this.children = children;
            this.childClosed = new boolean[children.size()];
            this.onClose = onClose;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public String currentItemIdentity() {
            advanceCompletedChildren();
            return cursor >= children.size()
                    ? name
                    : children.get(cursor).currentItemIdentity();
        }

        @Override
        public boolean isComplete() {
            advanceCompletedChildren();
            return cursor >= children.size();
        }

        @Override
        public void runNext() throws Exception {
            DailySettlementWorkUnit child = requireCurrentChild();
            child.runNext();
            advanceCompletedChildren();
        }

        @Override
        public void skipFailedItem() {
            DailySettlementWorkUnit child = requireCurrentChild();
            child.skipFailedItem();
            advanceCompletedChildren();
        }

        @Override
        public int maxRetries() {
            return requireCurrentChild().maxRetries();
        }

        @Override
        public synchronized void close() {
            if (closed) {
                return;
            }
            closed = true;
            for (int index = 0; index < children.size(); index++) {
                closeChildSafely(index);
            }
            try {
                onClose.run();
            } catch (RuntimeException | Error failure) {
                recordCloseFailure(failure);
            }
            rethrowCloseFailure();
        }

        private DailySettlementWorkUnit requireCurrentChild() {
            advanceCompletedChildren();
            if (cursor >= children.size()) {
                throw new IllegalStateException("Work unit is already complete: " + name);
            }
            return children.get(cursor);
        }

        private void advanceCompletedChildren() {
            while (cursor < children.size() && children.get(cursor).isComplete()) {
                closeChildSafely(cursor);
                cursor++;
            }
        }

        private void closeChildSafely(int index) {
            try {
                closeChild(index);
            } catch (RuntimeException | Error failure) {
                recordCloseFailure(failure);
            }
        }

        private void closeChild(int index) {
            if (childClosed[index]) {
                return;
            }
            childClosed[index] = true;
            children.get(index).close();
        }

        private void recordCloseFailure(Throwable failure) {
            if (closeFailure == null) {
                closeFailure = failure;
            } else if (closeFailure != failure) {
                closeFailure.addSuppressed(failure);
            }
        }

        private void rethrowCloseFailure() {
            if (closeFailure instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (closeFailure instanceof Error error) {
                throw error;
            }
        }
    }

    private static final class DeferredWorkUnit implements DailySettlementWorkUnit {
        private final String name;
        private final Supplier<? extends DailySettlementWorkUnit> supplier;
        private DailySettlementWorkUnit delegate;
        private Throwable creationFailure;
        private boolean initialized;
        private boolean skipped;
        private boolean closed;

        private DeferredWorkUnit(
                String name,
                Supplier<? extends DailySettlementWorkUnit> supplier) {
            this.name = name;
            this.supplier = supplier;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public String currentItemIdentity() {
            initialize();
            return creationFailure == null && delegate != null
                    ? delegate.currentItemIdentity()
                    : name;
        }

        @Override
        public boolean isComplete() {
            if (closed || skipped) {
                return true;
            }
            initialize();
            return creationFailure == null && delegate.isComplete();
        }

        @Override
        public void runNext() throws Exception {
            requireOpen();
            initialize();
            rethrowCreationFailure();
            delegate.runNext();
        }

        @Override
        public void skipFailedItem() {
            requireOpen();
            initialize();
            if (creationFailure != null) {
                skipped = true;
                return;
            }
            delegate.skipFailedItem();
        }

        @Override
        public int maxRetries() {
            requireOpen();
            initialize();
            return creationFailure == null ? delegate.maxRetries() : 0;
        }

        @Override
        public synchronized void close() {
            if (closed) {
                return;
            }
            closed = true;
            if (delegate != null) {
                delegate.close();
            }
        }

        private synchronized void initialize() {
            if (initialized || closed) {
                return;
            }
            initialized = true;
            try {
                delegate = Objects.requireNonNull(supplier.get(), "deferred work unit");
            } catch (RuntimeException | Error failure) {
                creationFailure = failure;
            }
        }

        private void requireOpen() {
            if (closed) {
                throw new IllegalStateException("Work unit is already closed: " + name);
            }
        }

        private void rethrowCreationFailure() {
            if (creationFailure instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (creationFailure instanceof Error error) {
                throw error;
            }
        }
    }
}
