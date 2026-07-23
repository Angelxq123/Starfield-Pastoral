package com.stardew.craft.manager;

import com.stardew.craft.time.settlement.DailySettlementWorkUnit;

import java.util.Objects;
import java.util.function.BooleanSupplier;

public final class PublicAreaDailyWorkUnits {
    private PublicAreaDailyWorkUnits() {
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

    @FunctionalInterface
    public interface ColumnConsumer {
        void accept(int x, int z);
    }
}
