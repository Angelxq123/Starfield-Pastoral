package com.stardew.craft.api.v1.internal.client;

import com.stardew.craft.api.v1.client.StardewConstructionProgressSnapshot;

import java.util.Optional;

/** Atomically replaced client cache; mutation is restricted to the sync payload. */
public final class StardewConstructionProgressCache {
    private static volatile StardewConstructionProgressSnapshot current;

    private StardewConstructionProgressCache() {
    }

    public static Optional<StardewConstructionProgressSnapshot> current() {
        return Optional.ofNullable(current);
    }

    public static void replace(StardewConstructionProgressSnapshot snapshot) {
        current = snapshot;
    }

    public static void clear() {
        current = null;
    }
}
