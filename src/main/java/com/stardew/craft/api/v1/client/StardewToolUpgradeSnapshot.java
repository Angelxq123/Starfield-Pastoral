package com.stardew.craft.api.v1.client;

import net.minecraft.resources.ResourceLocation;
import java.util.Objects;

/** Result tool ID and remaining overnight settlements, for the local player only. */
public record StardewToolUpgradeSnapshot(ResourceLocation resultItemId, int daysRemaining,
                                         StardewCalendarDate expectedReadyDate) {
    public StardewToolUpgradeSnapshot {
        Objects.requireNonNull(resultItemId, "resultItemId");
        Objects.requireNonNull(expectedReadyDate, "expectedReadyDate");
        if (daysRemaining < 0) throw new IllegalArgumentException("Negative upgrade days");
    }

    public boolean readyForPickup() {
        return daysRemaining == 0;
    }
}
