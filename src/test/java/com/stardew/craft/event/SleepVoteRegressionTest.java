package com.stardew.craft.event;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.ToolProvider;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class SleepVoteRegressionTest {
    private static final Path ROOT = Path.of(System.getProperty("stardewcraft.projectDir", "."));
    @TempDir Path temp;

    @Test
    void afkVoterStillCountsInDenominatorAlongsideTwoActiveFarmers() throws Exception {
        Class<?> fixture = compileTrackerFixture();
        assertEquals(3, fixture.getMethod("eligibleCount").invoke(null),
                "An accepted sleeper becoming AFK must not reduce a 100% vote from 3 to 2");
        assertEquals(2, fixture.getMethod("eligibleCountExcluding").invoke(null));
    }

    @Test
    void anotherFarmersVoteCannotReachTheUnconfirmedBedOccupant() throws Exception {
        Class<?> fixture = compileTrackerFixture();
        assertEquals(1, fixture.getMethod("progressRecipients").invoke(null),
                "Only the accepted voter may receive the UI-opening progress packet");
    }

    @Test
    void pendingVotesAreRecheckedOnRealTicksAndDimensionChanges() throws Exception {
        String tracker = source("SleepVoteTracker");
        assertTrue(method(tracker, "tickSleepEnergyRegen").contains("recheckEligibility(server)"));
        assertTrue(method(source("DimensionEventHandler"), "onPlayerChangeDimension")
                .contains("SleepVoteTracker.onPlayerDimensionChanged"));
    }

    @Test
    void delayedVoluntaryAdvanceRechecksVotesWhileTwoAmRemainsForced() throws Exception {
        String dimension = source("DimensionEventHandler");
        String schedule = method(dimension, "schedulePassOutAdvance");
        int callback = schedule.indexOf("schedule(server, delay, () ->");
        int clear = schedule.indexOf("SleepVoteTracker.clearVotes()", callback);
        assertTrue(callback >= 0 && clear > callback);
        String beforeClear = schedule.substring(callback, clear);
        assertTrue(beforeClear.contains("!forced") && beforeClear.contains("SleepVoteTracker.hasReachedThreshold(server)"),
                "Accepted cancellation before the barrier must prevent voluntary advance");
        assertTrue(method(dimension, "onLevelTick").contains("transitionPlayers, true"),
                "2AM must explicitly bypass the voluntary vote recheck");
    }

    private Class<?> compileTrackerFixture() throws Exception {
        String tracker = source("SleepVoteTracker");
        String fixture = """
                import java.util.*;
                public class VoteFixture {
                    static final UUID A = new UUID(0, 1), B = new UUID(0, 2), C = new UUID(0, 3);
                    static final Map<UUID, Integer> votes = new HashMap<>(Map.of(A, 900));
                    static final Set<UUID> passOutVotes = new HashSet<>();
                    record ServerPlayer(UUID id, boolean afk, boolean inDimension) {
                        UUID getUUID() { return id; }
                    }
                    record PlayerList(List<ServerPlayer> players) {
                        List<ServerPlayer> getPlayers() { return players; }
                    }
                    record MinecraftServer(PlayerList playerList) {
                        PlayerList getPlayerList() { return playerList; }
                    }
                    record SleepVoteUpdatePayload(int votedCount, int requiredCount) {}
                    static class PacketDistributor {
                        static final List<UUID> recipients = new ArrayList<>();
                        static void sendToPlayer(ServerPlayer p, SleepVoteUpdatePayload payload) {
                            recipients.add(p.id());
                        }
                    }
                    static boolean isInStardewDimension(ServerPlayer p) { return p.inDimension(); }
                    static boolean isAfk(ServerPlayer p, int timeout) { return p.afk(); }
                    static boolean hasVoted(ServerPlayer p) { return votes.containsKey(p.getUUID()); }
                    static MinecraftServer server() {
                        return new MinecraftServer(new PlayerList(List.of(
                            new ServerPlayer(A, true, true), new ServerPlayer(B, false, true),
                            new ServerPlayer(C, false, true), new ServerPlayer(new UUID(0, 4), false, false))));
                    }
                    public static int eligibleCount() { return countActiveStardewPlayers(server(), 60); }
                    public static int eligibleCountExcluding() { return countActiveStardewPlayersExcluding(server(), 60, C); }
                    public static int progressRecipients() {
                        PacketDistributor.recipients.clear();
                        broadcastVoteProgress(server(), 1, 3);
                        return PacketDistributor.recipients.size();
                    }
                """
                + method(tracker, "countActiveStardewPlayers")
                + method(tracker, "countActiveStardewPlayersExcluding")
                + method(tracker, "broadcastVoteProgress") + "}";
        Path java = temp.resolve("VoteFixture.java");
        Files.writeString(java, fixture);
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null,
                "-proc:none", "-d", temp.toString(), java.toString()));
        URLClassLoader loader = new URLClassLoader(new java.net.URL[]{temp.toUri().toURL()});
        return Class.forName("VoteFixture", true, loader);
    }

    private static String source(String name) throws Exception {
        return Files.readString(ROOT.resolve("src/main/java/com/stardew/craft/event/" + name + ".java"));
    }

    private static String method(String source, String name) {
        var matcher = java.util.regex.Pattern.compile("(?m)^    (?:public|private|static)[^\\n]*\\b"
                + name + "\\s*\\(").matcher(source);
        assertTrue(matcher.find(), "Missing production method " + name);
        int begin = matcher.start(), brace = source.indexOf('{', matcher.end()), depth = 1, end = brace + 1;
        while (depth > 0 && end < source.length()) {
            char ch = source.charAt(end++);
            if (ch == '{') depth++;
            else if (ch == '}') depth--;
        }
        return source.substring(begin, end);
    }
}
