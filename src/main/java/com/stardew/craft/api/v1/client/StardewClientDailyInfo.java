package com.stardew.craft.api.v1.client;

import com.stardew.craft.api.v1.internal.client.StardewDailyInfoCache;
import java.util.Optional;

/** Client read-only access. Empty before sync and after disconnect; never query server internals. */
public final class StardewClientDailyInfo {
    private StardewClientDailyInfo() {}

    public static Optional<StardewDailyInfoSnapshot> current() {
        return StardewDailyInfoCache.current();
    }
}
