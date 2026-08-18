package com.stardew.craft.interior;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Comparator;
import java.util.Objects;
import java.util.UUID;

final class PlayerInteriorReloadQueue {

    enum Kind {
        COMMUNITY_CENTER,
        GREENHOUSE,
        FARM_CAVE
    }

    record Work(UUID playerId, Kind kind) {
        Work {
            Objects.requireNonNull(playerId, "playerId");
            Objects.requireNonNull(kind, "kind");
        }
    }

    private final ArrayDeque<Work> pending = new ArrayDeque<>();

    void reset(
            Collection<UUID> communityCenters,
            Collection<UUID> greenhouses,
            Collection<UUID> farmCaves
    ) {
        pending.clear();
        appendSorted(communityCenters, Kind.COMMUNITY_CENTER);
        appendSorted(greenhouses, Kind.GREENHOUSE);
        appendSorted(farmCaves, Kind.FARM_CAVE);
    }

    Work poll() {
        return pending.pollFirst();
    }

    Work peek() {
        return pending.peekFirst();
    }

    void removeFirst() {
        if (pending.isEmpty()) {
            throw new IllegalStateException("interior reload queue is empty");
        }
        pending.removeFirst();
    }

    boolean isEmpty() {
        return pending.isEmpty();
    }

    int size() {
        return pending.size();
    }

    private void appendSorted(Collection<UUID> players, Kind kind) {
        Objects.requireNonNull(players, "players").stream()
                .map(playerId -> Objects.requireNonNull(playerId, "playerId"))
                .sorted(Comparator.naturalOrder())
                .map(playerId -> new Work(playerId, kind))
                .forEach(pending::addLast);
    }
}
