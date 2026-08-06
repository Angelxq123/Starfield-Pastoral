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
        return captureNextDay(time, sleepMinute, players, farms, List.of());
    }

    public static DailySettlementContext captureNextDay(
            StardewTimeManager time,
            int sleepMinute,
            Collection<UUID> players,
            Collection<UUID> farms,
            Collection<UUID> nonParticipantCleanupIds) {
        java.util.LinkedHashSet<UUID> allOnline = new java.util.LinkedHashSet<>(players);
        allOnline.addAll(nonParticipantCleanupIds);
        return captureNextDay(
                time, sleepMinute, players, farms, nonParticipantCleanupIds,
                allOnline, allOnline);
    }

    public static DailySettlementContext captureNextDay(
            StardewTimeManager time,
            int sleepMinute,
            Collection<UUID> players,
            Collection<UUID> farms,
            Collection<UUID> nonParticipantCleanupIds,
            Collection<UUID> allOnlinePlayerIds,
            Collection<UUID> valleyOnlinePlayerIds) {
        return captureNextDay(
                time, sleepMinute, players, farms, nonParticipantCleanupIds,
                allOnlinePlayerIds, valleyOnlinePlayerIds, "Sun");
    }

    public static DailySettlementContext captureNextDay(
            StardewTimeManager time,
            int sleepMinute,
            Collection<UUID> players,
            Collection<UUID> farms,
            Collection<UUID> nonParticipantCleanupIds,
            Collection<UUID> allOnlinePlayerIds,
            Collection<UUID> valleyOnlinePlayerIds,
            String previousWeather) {
        Objects.requireNonNull(time, "time");
        Objects.requireNonNull(players, "players");
        Objects.requireNonNull(farms, "farms");
        Objects.requireNonNull(nonParticipantCleanupIds, "nonParticipantCleanupIds");
        Objects.requireNonNull(allOnlinePlayerIds, "allOnlinePlayerIds");
        Objects.requireNonNull(valleyOnlinePlayerIds, "valleyOnlinePlayerIds");

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
                Set.copyOf(farms),
                List.copyOf(nonParticipantCleanupIds),
                List.copyOf(allOnlinePlayerIds),
                List.copyOf(valleyOnlinePlayerIds),
                previousWeather);
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

    public static DailySettlementContext withSeason(
            DailySettlementContext context, int season) {
        Objects.requireNonNull(context, "context");
        return withDate(context, context.year(), season);
    }

    public static DailySettlementContext withYear(
            DailySettlementContext context, int year) {
        Objects.requireNonNull(context, "context");
        return withDate(context, year, context.season());
    }

    public static DailySettlementContext withPlayers(
            DailySettlementContext context, Collection<UUID> players) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(players, "players");
        return new DailySettlementContext(
                context.absoluteDay(),
                context.year(),
                context.season(),
                context.day(),
                context.sleepMinute(),
                context.seasonChanged(),
                List.copyOf(players),
                context.farmOwnerIds(),
                context.nonParticipantCleanupIds(),
                context.allOnlinePlayerIds(),
                context.valleyOnlinePlayerIds(),
                context.previousWeather());
    }

    private static DailySettlementContext withDate(
            DailySettlementContext context, int year, int season) {
        return new DailySettlementContext(
                absoluteDay(year, season, context.day()),
                year,
                season,
                context.day(),
                context.sleepMinute(),
                context.seasonChanged(),
                context.playerIds(),
                context.farmOwnerIds(),
                context.nonParticipantCleanupIds(),
                context.allOnlinePlayerIds(),
                context.valleyOnlinePlayerIds(),
                context.previousWeather());
    }

    private static TargetDate after(int year, int season, int day) {
        TargetDate current = new TargetDate(year, season, day, false);
        if (current.day() < 28) {
            return new TargetDate(
                    current.year(), current.season(), current.day() + 1, false);
        }
        if (current.season() < 3) {
            return new TargetDate(
                    current.year(), current.season() + 1, 1, true);
        }
        return new TargetDate(current.year() + 1, 0, 1, true);
    }

    private static int absoluteDay(int year, int season, int day) {
        return Math.toIntExact((year - 1L) * 112L + season * 28L + day);
    }

    private record TargetDate(int year, int season, int day, boolean seasonChanged) {
        private TargetDate {
            if (year < 1) {
                throw new IllegalArgumentException("year must be at least 1");
            }
            if (season < 0 || season > 3) {
                throw new IllegalArgumentException("season must be between 0 and 3");
            }
            if (day < 1 || day > 28) {
                throw new IllegalArgumentException("day must be between 1 and 28");
            }
        }
    }
}
