package com.stardew.craft.api.v1.internal.client;

import com.stardew.craft.api.v1.client.StardewDailyInfoSnapshot;
import java.util.Optional;

/** Replaced atomically on the client game thread; no mutable player data escapes. */
public final class StardewDailyInfoCache {
    private static volatile StardewDailyInfoSnapshot current;
    private StardewDailyInfoCache() {}

    public static Optional<StardewDailyInfoSnapshot> current() { return Optional.ofNullable(current); }
    public static void replace(StardewDailyInfoSnapshot snapshot) { current = snapshot; }
    public static void clear() { current = null; }
}
