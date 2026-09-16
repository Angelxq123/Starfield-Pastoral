package com.stardew.craft.api.v1.client;

import net.minecraft.resources.ResourceLocation;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Immutable server-authoritative daily information. Calendar and forecast refer to the valley. */
public record StardewDailyInfoSnapshot(UUID playerId, StardewCalendarDate date,
        double dailyLuck, String tomorrowWeather, BerrySeason berrySeason,
        boolean booksellerToday, boolean travelingCartToday,
        List<ResourceLocation> birthdayNpcIds, Optional<StardewToolUpgradeSnapshot> toolUpgrade,
        Optional<StardewQueenOfSauceSnapshot> queenOfSauce) {
    public StardewDailyInfoSnapshot {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(date, "date");
        Objects.requireNonNull(tomorrowWeather, "tomorrowWeather");
        Objects.requireNonNull(berrySeason, "berrySeason");
        birthdayNpcIds = List.copyOf(birthdayNpcIds);
        Objects.requireNonNull(toolUpgrade, "toolUpgrade");
        Objects.requireNonNull(queenOfSauce, "queenOfSauce");
        if (!Double.isFinite(dailyLuck)) throw new IllegalArgumentException("Non-finite daily luck");
    }

    /** Compatibility constructor for consumers creating snapshots without cooking broadcast data. */
    public StardewDailyInfoSnapshot(UUID playerId, StardewCalendarDate date, double dailyLuck,
            String tomorrowWeather, BerrySeason berrySeason, boolean booksellerToday,
            boolean travelingCartToday, List<ResourceLocation> birthdayNpcIds,
            Optional<StardewToolUpgradeSnapshot> toolUpgrade) {
        this(playerId, date, dailyLuck, tomorrowWeather, berrySeason, booksellerToday,
                travelingCartToday, birthdayNpcIds, toolUpgrade, Optional.empty());
    }

    /** Same thresholds as the television fortune teller; temporary luck buffs are excluded. */
    public LuckLevel luckLevel() {
        if (dailyLuck == 0) return LuckLevel.ZERO;
        if (dailyLuck < -0.07) return LuckLevel.VERY_BAD;
        if (dailyLuck < -0.02) return LuckLevel.BAD;
        if (dailyLuck < 0.02) return LuckLevel.NEUTRAL;
        if (dailyLuck < 0.07) return LuckLevel.GOOD;
        return LuckLevel.VERY_GOOD;
    }

    public enum LuckLevel { VERY_BAD, BAD, NEUTRAL, ZERO, GOOD, VERY_GOOD }
    public enum BerrySeason { NONE, SALMONBERRY, BLACKBERRY }
}
