package com.stardew.craft.book;

import com.stardew.craft.time.StardewTimeManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Random;
import java.util.UUID;

public final class BooksellerSchedule {
    private static final int[][] POSSIBLE_DAYS = {
            {11, 12, 21, 22, 25},
            {9, 12, 18, 25, 27},
            {4, 7, 8, 9, 12, 19, 22, 25},
            {5, 11, 12, 19, 22, 24}
    };

    private BooksellerSchedule() {
    }

    public static boolean isToday(ServerLevel level) {
        StardewTimeManager time = StardewTimeManager.get();
        return isBooksellerDay(level, time.getCurrentYear(), time.getCurrentSeason(), time.getCurrentDay());
    }

    public static boolean isBooksellerDay(ServerLevel level, int year, int season, int day) {
        int[] days = daysForSeason(level, year, season);
        return days[0] == day || days[1] == day;
    }

    public static int[] daysForSeason(ServerLevel level, int year, int season) {
        int seasonIndex = Math.floorMod(season, POSSIBLE_DAYS.length);
        int[] possibleDays = POSSIBLE_DAYS[seasonIndex];
        Random random = new Random(createSeed(level, year, seasonIndex));
        int index = random.nextInt(possibleDays.length);
        return new int[] {
                possibleDays[index],
                possibleDays[(index + possibleDays.length / 2) % possibleDays.length]
        };
    }

    public static void onNewDay(ServerLevel level, List<ServerPlayer> players) {
        onNewDayForPlayers(level, players.stream().map(ServerPlayer::getUUID).toList());
    }

    public static void onNewDayForPlayers(ServerLevel level, List<UUID> playerIds) {
        StardewTimeManager time = StardewTimeManager.get();
        int absoluteDay = time.getAbsoluteDay();
        onNewDayForPlayers(
                level, playerIds, time.getCurrentYear(), time.getCurrentSeason(),
                time.getCurrentDay(), absoluteDay);
        deliverPendingNoticesForPlayers(level.getServer(), playerIds, absoluteDay);
    }

    public static void onNewDayForPlayers(
            ServerLevel level,
            List<UUID> playerIds,
            int year,
            int season,
            int day,
            int absoluteDay) {
        if (!isBooksellerDay(level, year, season, day)) {
            return;
        }
        for (UUID playerId : List.copyOf(playerIds)) {
            com.stardew.craft.player.PlayerDataManager.getPlayerData(playerId)
                    .queueBooksellerNotice(absoluteDay);
        }
    }

    public static void onPlayerLogin(ServerPlayer player) {
        int absoluteDay = StardewTimeManager.get().getAbsoluteDay();
        deliverPendingNotice(
                com.stardew.craft.player.PlayerDataManager.getPlayerData(player),
                absoluteDay,
                () -> com.stardew.craft.network.GlobalHudMessagePayload.sendTo(
                        player, Component.translatable("stardewcraft.bookseller.in_town")));
    }

    public static void deliverPendingNoticesForPlayers(
            net.minecraft.server.MinecraftServer server,
            List<UUID> playerIds,
            int publishedAbsoluteDay) {
        for (UUID playerId : List.copyOf(playerIds)) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player != null) {
                deliverPendingNotice(
                        com.stardew.craft.player.PlayerDataManager.getPlayerData(playerId),
                        publishedAbsoluteDay,
                        () -> com.stardew.craft.network.GlobalHudMessagePayload.sendTo(
                                player,
                                Component.translatable("stardewcraft.bookseller.in_town")));
            }
        }
    }

    static boolean deliverPendingNotice(
            com.stardew.craft.player.PlayerStardewData data,
            int publishedAbsoluteDay,
            Runnable sender) {
        java.util.Objects.requireNonNull(data, "data");
        java.util.Objects.requireNonNull(sender, "sender");
        int pendingDay = data.getPendingBooksellerNoticeDay();
        if (pendingDay != Integer.MIN_VALUE && pendingDay < publishedAbsoluteDay) {
            data.acknowledgeBooksellerNotice(pendingDay);
            return false;
        }
        if (!data.hasPendingBooksellerNotice(publishedAbsoluteDay)) {
            return false;
        }
        if (!data.acknowledgeBooksellerNotice(publishedAbsoluteDay)) {
            return false;
        }
        sender.run();
        return true;
    }

    private static long createSeed(ServerLevel level, int year, int seasonIndex) {
        long seed = 0xCBF29CE484222325L;
        seed = mix(seed, (long) year * 11L);
        seed = mix(seed, level.getSeed());
        seed = mix(seed, seasonIndex);
        return seed;
    }

    private static long mix(long seed, long value) {
        long mixed = value + 0x9E3779B97F4A7C15L + (seed << 6) + (seed >>> 2);
        return seed ^ mixed;
    }
}
