package com.stardew.craft.time.settlement;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record DailySettlementContext(
        int absoluteDay,
        int year,
        int season,
        int day,
        int sleepMinute,
        boolean seasonChanged,
        List<UUID> playerIds,
        Set<UUID> farmOwnerIds) {

    public DailySettlementContext {
        Objects.requireNonNull(playerIds, "playerIds");
        Objects.requireNonNull(farmOwnerIds, "farmOwnerIds");
        if (year < 1) {
            throw new IllegalArgumentException("year must be at least 1");
        }
        if (season < 0 || season > 3) {
            throw new IllegalArgumentException("season must be between 0 and 3");
        }
        if (day < 1 || day > 28) {
            throw new IllegalArgumentException("day must be between 1 and 28");
        }

        long expectedAbsoluteDay = (year - 1L) * 112L + season * 28L + day;
        if (absoluteDay != expectedAbsoluteDay) {
            throw new IllegalArgumentException(
                    "absoluteDay must match year, season, and day: " + expectedAbsoluteDay);
        }

        playerIds = List.copyOf(playerIds);
        farmOwnerIds = Set.copyOf(farmOwnerIds);
    }
}
