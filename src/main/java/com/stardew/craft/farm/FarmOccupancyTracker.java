package com.stardew.craft.farm;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

final class FarmOccupancyTracker<P> {

    record SlotCount(int slot, int count) {}

    record Transition(SlotCount current, Optional<SlotCount> previous, boolean changed) {
        int slot() {
            return current.slot();
        }

        int count() {
            return current.count();
        }
    }

    private final Map<P, Integer> playerSlots = new HashMap<>();
    private final Map<Integer, Integer> slotCounts = new HashMap<>();

    Transition enter(P player, int slot) {
        Objects.requireNonNull(player, "player");
        validateSlot(slot);

        Integer previousSlot = playerSlots.get(player);
        if (previousSlot != null && previousSlot == slot) {
            return new Transition(new SlotCount(slot, count(slot)), Optional.empty(), false);
        }

        Optional<SlotCount> previous = Optional.empty();
        if (previousSlot != null) {
            previous = Optional.of(new SlotCount(previousSlot, decrement(previousSlot)));
        }

        playerSlots.put(player, slot);
        int count = slotCounts.merge(slot, 1, Integer::sum);
        return new Transition(new SlotCount(slot, count), previous, true);
    }

    Optional<Transition> leave(P player) {
        Objects.requireNonNull(player, "player");

        Integer slot = playerSlots.get(player);
        if (slot == null) {
            return Optional.empty();
        }
        int count = decrement(slot);
        playerSlots.remove(player);
        return Optional.of(new Transition(
            new SlotCount(slot, count), Optional.empty(), true));
    }

    int count(int slot) {
        validateSlot(slot);
        return slotCounts.getOrDefault(slot, 0);
    }

    boolean isOccupied(int slot) {
        validateSlot(slot);
        return slotCounts.containsKey(slot);
    }

    OptionalInt slotOf(P player) {
        Objects.requireNonNull(player, "player");
        Integer slot = playerSlots.get(player);
        return slot == null ? OptionalInt.empty() : OptionalInt.of(slot);
    }

    void clear() {
        playerSlots.clear();
        slotCounts.clear();
    }

    private int decrement(int slot) {
        Integer current = slotCounts.get(slot);
        if (current == null || current <= 0) {
            throw new IllegalStateException("Missing positive occupancy count for slot " + slot);
        }

        int count = current - 1;
        if (count == 0) {
            slotCounts.remove(slot);
            return 0;
        }
        slotCounts.put(slot, count);
        return count;
    }

    private static void validateSlot(int slot) {
        if (slot < 0) {
            throw new IllegalArgumentException("slot must be non-negative");
        }
    }
}
