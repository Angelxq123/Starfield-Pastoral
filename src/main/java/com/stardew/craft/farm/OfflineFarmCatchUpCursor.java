package com.stardew.craft.farm;

import java.util.List;
import java.util.Objects;

final class OfflineFarmCatchUpCursor<T> {
    private final int targetDay;
    private final List<T> crops;
    private final List<T> trees;
    private final List<T> sprinklers;
    private final Operations<T> operations;

    private int currentDay;
    private Phase phase = Phase.CROPS;
    private int itemIndex;
    private boolean complete;

    OfflineFarmCatchUpCursor(
            int completedDay,
            int targetDay,
            List<T> crops,
            List<T> trees,
            List<T> sprinklers,
            Operations<T> operations) {
        if (targetDay < completedDay) {
            throw new IllegalArgumentException("targetDay must not precede completedDay");
        }
        this.targetDay = targetDay;
        this.crops = List.copyOf(Objects.requireNonNull(crops, "crops"));
        this.trees = List.copyOf(Objects.requireNonNull(trees, "trees"));
        this.sprinklers = List.copyOf(Objects.requireNonNull(sprinklers, "sprinklers"));
        this.operations = Objects.requireNonNull(operations, "operations");
        this.currentDay = completedDay + 1;
        this.complete = completedDay == targetDay;
    }

    boolean runNext() {
        normalizePhase();
        if (complete) {
            return false;
        }
        switch (phase) {
            case CROPS -> operations.growCrop(currentDay, crops.get(itemIndex));
            case TREES -> operations.growTree(currentDay, trees.get(itemIndex));
            case SPRINKLERS -> operations.waterSprinkler(sprinklers.get(itemIndex));
            case COMMIT -> {
                operations.commitDay(currentDay);
                currentDay++;
                itemIndex = 0;
                phase = Phase.CROPS;
                complete = currentDay > targetDay;
                return true;
            }
        }
        itemIndex++;
        return true;
    }

    boolean isComplete() {
        return complete;
    }

    boolean canSkipFailedItem() {
        normalizePhase();
        return !complete && phase != Phase.COMMIT;
    }

    void skipFailedItem() {
        if (!canSkipFailedItem()) {
            throw new IllegalStateException("A catch-up day commit cannot be skipped");
        }
        itemIndex++;
    }

    private void normalizePhase() {
        while (!complete) {
            switch (phase) {
                case CROPS -> {
                    if (itemIndex < crops.size()) {
                        return;
                    }
                    phase = Phase.TREES;
                    itemIndex = 0;
                }
                case TREES -> {
                    if (itemIndex < trees.size()) {
                        return;
                    }
                    phase = Phase.SPRINKLERS;
                    itemIndex = 0;
                }
                case SPRINKLERS -> {
                    if (currentDay == targetDay && itemIndex < sprinklers.size()) {
                        return;
                    }
                    phase = Phase.COMMIT;
                    itemIndex = 0;
                }
                case COMMIT -> {
                    return;
                }
            }
        }
    }

    interface Operations<T> {
        void growCrop(int absoluteDay, T crop);

        void growTree(int absoluteDay, T tree);

        void waterSprinkler(T sprinkler);

        void commitDay(int absoluteDay);
    }

    private enum Phase {
        CROPS,
        TREES,
        SPRINKLERS,
        COMMIT
    }
}
