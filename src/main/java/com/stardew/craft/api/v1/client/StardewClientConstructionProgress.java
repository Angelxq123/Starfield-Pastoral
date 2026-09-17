package com.stardew.craft.api.v1.client;

import com.stardew.craft.api.v1.internal.client.StardewConstructionProgressCache;

import java.util.Optional;

/** Client read-only access to server-authoritative Robin construction progress. */
public final class StardewClientConstructionProgress {
    private StardewClientConstructionProgress() {
    }

    /** Empty before the first sync and after disconnect; a synchronized empty list means no active work. */
    public static Optional<StardewConstructionProgressSnapshot> current() {
        return StardewConstructionProgressCache.current();
    }
}
