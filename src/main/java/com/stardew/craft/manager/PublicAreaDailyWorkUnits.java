package com.stardew.craft.manager;

import com.stardew.craft.time.settlement.DailySettlementWorkUnit;
import net.minecraft.server.level.ServerLevel;

import java.util.Objects;
import java.util.function.BooleanSupplier;

public final class PublicAreaDailyWorkUnits {
    private PublicAreaDailyWorkUnits() {
    }

    static boolean isChunkLoadedNow(ServerLevel level, int blockX, int blockZ) {
        return level.getChunkSource().getChunkNow(blockX >> 4, blockZ >> 4) != null;
    }

    public static DailySettlementWorkUnit rectangle(
            String name,
            int minX,
            int minZ,
            int maxX,
            int maxZ,
            ColumnConsumer consumer,
            BooleanSupplier stopEarly,
            Runnable onClose) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(consumer, "consumer");
        Objects.requireNonNull(stopEarly, "stopEarly");
        Objects.requireNonNull(onClose, "onClose");
        if (maxX < minX || maxZ < minZ) {
            throw new IllegalArgumentException("rectangle bounds must be ordered");
        }

        int width = maxX - minX + 1;
        int itemCount = Math.multiplyExact(width, maxZ - minZ + 1);
        return new DailySettlementWorkUnit() {
            private int cursor;
            private boolean closed;

            @Override
            public String name() {
                return name;
            }

            @Override
            public String currentItemIdentity() {
                if (isComplete()) {
                    return name;
                }
                int x = minX + cursor % width;
                int z = minZ + cursor / width;
                return name + ":" + x + "," + z;
            }

            @Override
            public boolean isComplete() {
                return cursor >= itemCount || stopEarly.getAsBoolean();
            }

            @Override
            public void runNext() {
                requireCurrentItem();
                int x = minX + cursor % width;
                int z = minZ + cursor / width;
                consumer.accept(x, z);
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
        };
    }

    /**
     * Creates a resumable cursor over the chunks intersecting a block rectangle.
     * The consumer is responsible for using only already-loaded chunks.
     */
    public static DailySettlementWorkUnit chunkRectangle(
            String name,
            int minX,
            int minZ,
            int maxX,
            int maxZ,
            ChunkConsumer consumer,
            BooleanSupplier stopEarly,
            Runnable onClose) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(consumer, "consumer");
        Objects.requireNonNull(stopEarly, "stopEarly");
        Objects.requireNonNull(onClose, "onClose");
        if (maxX < minX || maxZ < minZ) {
            throw new IllegalArgumentException("rectangle bounds must be ordered");
        }

        int minChunkX = minX >> 4;
        int minChunkZ = minZ >> 4;
        int width = (maxX >> 4) - minChunkX + 1;
        int itemCount = Math.multiplyExact(width, (maxZ >> 4) - minChunkZ + 1);
        return new DailySettlementWorkUnit() {
            private int cursor;
            private boolean closed;

            @Override
            public String name() {
                return name;
            }

            @Override
            public String currentItemIdentity() {
                if (isComplete()) {
                    return name;
                }
                return name + ":" + (minChunkX + cursor % width)
                        + "," + (minChunkZ + cursor / width);
            }

            @Override
            public boolean isComplete() {
                return cursor >= itemCount || stopEarly.getAsBoolean();
            }

            @Override
            public void runNext() {
                requireCurrentItem();
                int chunkX = minChunkX + cursor % width;
                int chunkZ = minChunkZ + cursor / width;
                consumer.accept(chunkX, chunkZ);
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
        };
    }

    public static DailySettlementWorkUnit forageAttempts(
            String name,
            int slots,
            int maxAttempts,
            IndexedAttempt operation,
            Runnable onClose) {
        return new ForageAttemptWorkUnit(
                name, slots, maxAttempts, operation, onClose);
    }

    public static DailySettlementWorkUnit cappedRectangle(
            String name,
            int minX,
            int minZ,
            int maxX,
            int maxZ,
            int cap,
            PlacementAttempt operation,
            Runnable onClose) {
        return cappedRectangle(
                name, minX, minZ, maxX, maxZ, cap, operation, () -> false, onClose);
    }

    public static DailySettlementWorkUnit cappedRectangle(
            String name,
            int minX,
            int minZ,
            int maxX,
            int maxZ,
            int cap,
            PlacementAttempt operation,
            BooleanSupplier stopEarly,
            Runnable onClose) {
        return new CappedRectangleWorkUnit(
                name, minX, minZ, maxX, maxZ, cap, operation, stopEarly, onClose);
    }

    public static DailySettlementWorkUnit decayingAttempts(
            String name,
            boolean winter,
            DecayingAttempt operation,
            Runnable onClose) {
        return new DecayingAttemptWorkUnit(name, winter, operation, onClose);
    }

    static final class ForageAttemptWorkUnit implements DailySettlementWorkUnit {
        private final String name;
        private final int slots;
        private final int maxAttempts;
        private final IndexedAttempt operation;
        private final Runnable onClose;
        private int slot;
        private int attempt;
        private boolean closed;

        ForageAttemptWorkUnit(
                String name,
                int slots,
                int maxAttempts,
                IndexedAttempt operation,
                Runnable onClose) {
            this.name = Objects.requireNonNull(name, "name");
            if (slots < 0) throw new IllegalArgumentException("slots must not be negative");
            if (maxAttempts < 1) throw new IllegalArgumentException("maxAttempts must be positive");
            this.slots = slots;
            this.maxAttempts = maxAttempts;
            this.operation = Objects.requireNonNull(operation, "operation");
            this.onClose = Objects.requireNonNull(onClose, "onClose");
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public String currentItemIdentity() {
            return isComplete() ? name : name + ":" + slot + ":" + attempt;
        }

        @Override
        public boolean isComplete() {
            return slot >= slots;
        }

        @Override
        public void runNext() {
            requireCurrentItem();
            advance(operation.trySpawn(slot, attempt));
        }

        @Override
        public void skipFailedItem() {
            requireCurrentItem();
            advance(false);
        }

        @Override
        public synchronized void close() {
            if (closed) return;
            closed = true;
            onClose.run();
        }

        private void advance(boolean placed) {
            attempt++;
            if (placed || attempt >= maxAttempts) {
                slot++;
                attempt = 0;
            }
        }

        private void requireCurrentItem() {
            if (isComplete()) {
                throw new IllegalStateException("Work unit is already complete: " + name);
            }
        }
    }

    static final class CappedRectangleWorkUnit implements DailySettlementWorkUnit {
        private final String name;
        private final int minX;
        private final int minZ;
        private final int width;
        private final int itemCount;
        private final int cap;
        private final PlacementAttempt operation;
        private final BooleanSupplier stopEarly;
        private final Runnable onClose;
        private int cursor;
        private int placed;
        private boolean closed;

        CappedRectangleWorkUnit(
                String name,
                int minX,
                int minZ,
                int maxX,
                int maxZ,
                int cap,
                PlacementAttempt operation,
                BooleanSupplier stopEarly,
                Runnable onClose) {
            this.name = Objects.requireNonNull(name, "name");
            if (maxX < minX || maxZ < minZ) {
                throw new IllegalArgumentException("rectangle bounds must be ordered");
            }
            if (cap < 0) throw new IllegalArgumentException("cap must not be negative");
            this.minX = minX;
            this.minZ = minZ;
            this.width = maxX - minX + 1;
            this.itemCount = Math.multiplyExact(width, maxZ - minZ + 1);
            this.cap = cap;
            this.operation = Objects.requireNonNull(operation, "operation");
            this.stopEarly = Objects.requireNonNull(stopEarly, "stopEarly");
            this.onClose = Objects.requireNonNull(onClose, "onClose");
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public String currentItemIdentity() {
            if (isComplete()) return name;
            int x = minX + cursor % width;
            int z = minZ + cursor / width;
            return name + ":" + x + "," + z;
        }

        @Override
        public boolean isComplete() {
            return cursor >= itemCount || placed >= cap || stopEarly.getAsBoolean();
        }

        @Override
        public void runNext() {
            requireCurrentItem();
            int x = minX + cursor % width;
            int z = minZ + cursor / width;
            boolean accepted = operation.tryPlace(x, z);
            cursor++;
            if (accepted) placed++;
        }

        @Override
        public void skipFailedItem() {
            requireCurrentItem();
            cursor++;
        }

        @Override
        public synchronized void close() {
            if (closed) return;
            closed = true;
            onClose.run();
        }

        private void requireCurrentItem() {
            if (isComplete()) {
                throw new IllegalStateException("Work unit is already complete: " + name);
            }
        }
    }

    static final class DecayingAttemptWorkUnit implements DailySettlementWorkUnit {
        private final String name;
        private final boolean winter;
        private final DecayingAttempt operation;
        private final Runnable onClose;
        private int cursor;
        private double chance = 1.0D;
        private boolean complete;
        private boolean closed;

        DecayingAttemptWorkUnit(
                String name,
                boolean winter,
                DecayingAttempt operation,
                Runnable onClose) {
            this.name = Objects.requireNonNull(name, "name");
            this.winter = winter;
            this.operation = Objects.requireNonNull(operation, "operation");
            this.onClose = Objects.requireNonNull(onClose, "onClose");
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public String currentItemIdentity() {
            return complete ? name : name + ":" + cursor;
        }

        @Override
        public boolean isComplete() {
            return complete;
        }

        @Override
        public void runNext() {
            requireCurrentItem();
            if (!operation.tryRun(cursor, chance)) {
                complete = true;
                return;
            }
            advance();
        }

        @Override
        public void skipFailedItem() {
            requireCurrentItem();
            advance();
        }

        @Override
        public synchronized void close() {
            if (closed) return;
            closed = true;
            onClose.run();
        }

        private void advance() {
            chance *= 0.75D;
            if (winter) chance += 0.10D;
            cursor++;
        }

        private void requireCurrentItem() {
            if (complete) {
                throw new IllegalStateException("Work unit is already complete: " + name);
            }
        }
    }

    @FunctionalInterface
    public interface ColumnConsumer {
        void accept(int x, int z);
    }

    @FunctionalInterface
    public interface IndexedAttempt {
        boolean trySpawn(int slot, int attempt);
    }

    @FunctionalInterface
    public interface PlacementAttempt {
        boolean tryPlace(int x, int z);
    }

    @FunctionalInterface
    public interface ChunkConsumer {
        void accept(int chunkX, int chunkZ);
    }

    @FunctionalInterface
    public interface DecayingAttempt {
        boolean tryRun(int cursor, double chance);
    }
}
