package com.stardew.craft.api.v1.client;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Complete bounded replacement view of Robin orders the local player may manage. */
public record StardewConstructionProgressSnapshot(
        UUID playerId,
        int totalOrderCount,
        List<StardewConstructionOrderSnapshot> orders
) {
    public StardewConstructionProgressSnapshot {
        playerId = Objects.requireNonNull(playerId, "playerId");
        orders = List.copyOf(orders);
        if (totalOrderCount < orders.size()) {
            throw new IllegalArgumentException("totalOrderCount is smaller than the synchronized list");
        }
    }

    public boolean truncated() {
        return totalOrderCount > orders.size();
    }

    public Optional<StardewConstructionOrderSnapshot> find(UUID buildingId) {
        if (buildingId == null) {
            return Optional.empty();
        }
        return orders.stream().filter(order -> order.buildingId().equals(buildingId)).findFirst();
    }
}
