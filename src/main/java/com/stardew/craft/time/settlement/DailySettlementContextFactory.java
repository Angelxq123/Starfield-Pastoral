package com.stardew.craft.time.settlement;

import com.stardew.craft.time.StardewTimeManager;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class DailySettlementContextFactory {
    private DailySettlementContextFactory() {
    }

    public static DailySettlementContext captureNextDay(
            StardewTimeManager time,
            int sleepMinute,
            Collection<UUID> players,
            Collection<UUID> farms) {
        Objects.requireNonNull(time, "time");
        Objects.requireNonNull(players, "players");
        Objects.requireNonNull(farms, "farms");

        TargetDate target = after(
                time.getCurrentYear(), time.getCurrentSeason(), time.getCurrentDay());
        return new DailySettlementContext(
                absoluteDay(target.year(), target.season(), target.day()),
                target.year(),
                target.season(),
                target.day(),
                sleepMinute,
                target.seasonChanged(),
                List.copyOf(players),
                Set.copyOf(farms));
    }

    public static DailySettlementContext captureCurrentDay(StardewTimeManager time) {
        Objects.requireNonNull(time, "time");
        int year = time.getCurrentYear();
        int season = time.getCurrentSeason();
        int day = time.getCurrentDay();
        return new DailySettlementContext(
                absoluteDay(year, season, day),
                year,
                season,
                day,
                time.getCurrentTime(),
                false,
                List.of(),
                Set.of());
    }

    private static TargetDate after(int year, int season, int day) {
        if (day < 28) {
            return new TargetDate(year, season, day + 1, false);
        }
        if (season < 3) {
            return new TargetDate(year, season + 1, 1, true);
        }
        return new TargetDate(year + 1, 0, 1, true);
    }

    private static int absoluteDay(int year, int season, int day) {
        return Math.toIntExact((year - 1L) * 112L + season * 28L + day);
    }

    private record TargetDate(int year, int season, int day, boolean seasonChanged) {
    }
}
