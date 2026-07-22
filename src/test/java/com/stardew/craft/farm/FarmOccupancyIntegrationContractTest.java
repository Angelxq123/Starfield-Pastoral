package com.stardew.craft.farm;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FarmOccupancyIntegrationContractTest {

    @Test
    void entryHandlerTracksTheFarmSelectedByTargetOwner() throws IOException {
        String body = methodBody(source(
            "src/main/java/com/stardew/craft/network/payload/FarmEntryRequestPayload.java"),
            "public static void handle(FarmEntryRequestPayload payload, IPayloadContext context)");

        assertTrue(body.contains("registry.getFarm(payload.targetOwner)"));
        assertTrue(Pattern.compile(
            "FarmInstance farm = registry\\.getFarm\\(payload\\.targetOwner\\).*"
                + "onPlayerEnterFarm\\(stardewLevel, player, farm\\)",
            Pattern.DOTALL).matcher(body).find());
    }

    @Test
    void farmExitUsesTrackedLeaveWithoutRegistryLookup() throws IOException {
        String body = methodBody(source(
            "src/main/java/com/stardew/craft/event/InteriorPortalInteractionEvents.java"),
            "private static void handleFarmExit(ServerPlayer player, String exitId)");

        assertFalse(body.contains("getFarmForPlayer"));
        assertTrue(Pattern.compile(
            "onPlayerLeaveFarm\\(\\s*player\\.serverLevel\\(\\),\\s*player\\s*\\)")
            .matcher(body).find());
    }

    @Test
    void logoutFarmCleanupUsesTrackedLogoutForEveryPlayer() throws IOException {
        String body = methodBody(source(
            "src/main/java/com/stardew/craft/player/PlayerDataEventHandler.java"),
            "public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event)");

        assertFalse(body.contains("FarmInstanceRegistry"));
        assertFalse(body.contains("getFarmForPlayer"));
        assertFalse(body.contains("FarmInstance farm"));
        assertTrue(body.contains("FarmChunkManager.get().onPlayerLogout(player)"));
    }

    @Test
    void managerLeaveAndLogoutUseOnlyTrackedMembership() throws IOException {
        String manager = source("src/main/java/com/stardew/craft/farm/FarmChunkManager.java");
        String leave = methodBody(manager,
            "public void onPlayerLeaveFarm(ServerLevel level, ServerPlayer player)");
        String logout = methodBody(manager,
            "public void onPlayerLogout(ServerPlayer player)");

        assertFalse(leave.contains("FarmInstanceRegistry"));
        assertFalse(leave.contains("getFarmForPlayer"));
        assertFalse(logout.contains("FarmInstanceRegistry"));
        assertFalse(logout.contains("getFarmForPlayer"));
        assertTrue(logout.contains("onPlayerLeaveFarm(player.serverLevel(), player)"));
    }

    @Test
    void legacyLeaveOverloadOnlyDelegatesToTrackedLeave() throws IOException {
        String body = methodBody(source(
            "src/main/java/com/stardew/craft/farm/FarmChunkManager.java"),
            "public void onPlayerLeaveFarm(ServerLevel level, ServerPlayer player, FarmInstance farm)");

        assertEquals("onPlayerLeaveFarm(level, player);", body.trim());
    }

    @Test
    void managerQueriesAndStopCleanupDelegateToOccupancyTracker() throws IOException {
        String manager = source("src/main/java/com/stardew/craft/farm/FarmChunkManager.java");
        String loaded = methodBody(manager, "public boolean isFarmLoaded(int slotIndex)");
        String count = methodBody(manager, "public int getPlayerCount(int slotIndex)");
        String stop = methodBody(manager, "public void onServerStopping(ServerLevel level)");

        assertEquals("return occupancy.isOccupied(slotIndex);", loaded.trim());
        assertEquals("return occupancy.count(slotIndex);", count.trim());
        assertTrue(Pattern.compile(
            "finally\\s*\\{\\s*try\\s*\\{\\s*temporaryChunkLeases\\.closeAll\\(level\\);\\s*}"
                + "\\s*finally\\s*\\{\\s*occupancy\\.clear\\(\\);\\s*}\\s*}",
            Pattern.DOTALL).matcher(stop).find());
    }

    @Test
    void subscribedServerStopAlwaysDelegatesFarmCleanup() throws IOException {
        String body = methodBody(source(
            "src/main/java/com/stardew/craft/player/PlayerDataEventHandler.java"),
            "public static void onServerStopping(ServerStoppingEvent event)");
        var cleanup = Pattern.compile(
            "try\\s*\\{(?<caches>.*?)\\}\\s*finally\\s*\\{(?<farm>.*?)\\}",
            Pattern.DOTALL).matcher(body);

        assertTrue(cleanup.find(), "Static server-stop cleanup must guarantee farm cleanup with finally");
        assertTrue(cleanup.group("caches").contains("InteriorSubspaceManager.clearPortalRegistry()"));
        assertTrue(cleanup.group("farm").contains(
            "event.getServer().getLevel(com.stardew.craft.core.ModDimensions.STARDEW_VALLEY)"));
        assertTrue(cleanup.group("farm").contains(
            "FarmChunkManager.get().onServerStopping(stardewLevel)"));
    }

    @Test
    void legacyLogoutOverloadDelegatesToTrackedLogout() throws IOException {
        String body = methodBody(source(
            "src/main/java/com/stardew/craft/farm/FarmChunkManager.java"),
            "public void onPlayerLogout(ServerLevel level, ServerPlayer player)");

        assertEquals("onPlayerLogout(player);", body.trim());
    }

    private static String source(String relativePath) throws IOException {
        Path projectDir = Path.of(System.getProperty("stardewcraft.projectDir"));
        return Files.readString(projectDir.resolve(relativePath));
    }

    private static String methodBody(String source, String declaration) {
        int declarationStart = source.indexOf(declaration);
        assertTrue(declarationStart >= 0, "Missing method declaration: " + declaration);
        int bodyStart = source.indexOf('{', declarationStart + declaration.length());
        assertTrue(bodyStart >= 0, "Missing method body: " + declaration);

        int depth = 1;
        for (int index = bodyStart + 1; index < source.length(); index++) {
            char current = source.charAt(index);
            if (current == '{') {
                depth++;
            } else if (current == '}' && --depth == 0) {
                return source.substring(bodyStart + 1, index);
            }
        }
        throw new AssertionError("Unclosed method body: " + declaration);
    }
}
