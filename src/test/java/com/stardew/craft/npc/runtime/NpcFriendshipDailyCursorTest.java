package com.stardew.craft.npc.runtime;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NpcFriendshipDailyCursorTest {
    private static final Path PROJECT = Path.of(
            System.getProperty("stardewcraft.projectDir", "."));

    @Test
    void oneCursorItemSettlesOnlyItsFrozenPlayer() throws Exception {
        NpcFriendshipDataManager manager = new NpcFriendshipDataManager();
        UUID firstPlayer = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID secondPlayer = UUID.fromString("00000000-0000-0000-0000-000000000002");
        NpcFriendshipDataManager.FriendshipState first =
                manager.getOrCreate(firstPlayer, "lewis");
        NpcFriendshipDataManager.FriendshipState second =
                manager.getOrCreate(secondPlayer, "lewis");
        first.addPoints(100, 2_500);
        second.addPoints(100, 2_500);

        manager.settlePlayerNewDay(
                firstPlayer,
                10,
                2,
                ignored -> true,
                ignored -> false,
                ignored -> 2_500);

        assertEquals(98, first.points());
        assertEquals(100, second.points());
    }

    @Test
    void playerSettlementPreservesDecayAndWeeklyGiftBonusOrder() {
        NpcFriendshipDataManager manager = new NpcFriendshipDataManager();
        UUID playerId = UUID.fromString("00000000-0000-0000-0000-000000000003");
        NpcFriendshipDataManager.FriendshipState state =
                manager.getOrCreate(playerId, "lewis");
        state.addPoints(100, 2_500);
        state.applyGiftCounters(8, 1);
        state.applyGiftCounters(9, 1);

        manager.settlePlayerNewDay(
                playerId,
                10,
                2,
                ignored -> true,
                ignored -> false,
                ignored -> 2_500);

        assertEquals(108, state.points());
        assertEquals(0, state.giftsThisWeek());
        assertEquals(2, state.lastGiftWeekKey());
    }

    @Test
    void failedPlayerCalculationLeavesAllNpcStatesUnchangedForRetry() {
        NpcFriendshipDataManager manager = new NpcFriendshipDataManager();
        UUID playerId = UUID.fromString("00000000-0000-0000-0000-000000000004");
        NpcFriendshipDataManager.FriendshipState lewis =
                manager.getOrCreate(playerId, "lewis");
        NpcFriendshipDataManager.FriendshipState robin =
                manager.getOrCreate(playerId, "robin");
        lewis.addPoints(100, 2_500);
        robin.addPoints(100, 2_500);
        AtomicInteger maxPointCalls = new AtomicInteger();

        assertThrows(IllegalStateException.class, () -> manager.settlePlayerNewDay(
                playerId,
                10,
                2,
                ignored -> true,
                ignored -> false,
                ignored -> {
                    if (maxPointCalls.incrementAndGet() == 2) {
                        throw new IllegalStateException("test failure");
                    }
                    return 2_500;
                }));

        assertEquals(100, lewis.points());
        assertEquals(100, robin.points());
    }

    @Test
    void productionPlanBuildsFriendshipPlayerCursorInsteadOfAtomicBatch() throws Exception {
        String service = Files.readString(PROJECT.resolve(
                "src/main/java/com/stardew/craft/npc/runtime/NpcFriendshipDailyService.java"));
        String plan = Files.readString(PROJECT.resolve(
                "src/main/java/com/stardew/craft/time/settlement/DailySettlementPlanFactory.java"));

        assertTrue(service.contains("DailySettlementWorkUnits.cursor"));
        assertTrue(service.contains("playerIdsSnapshot"));
        assertTrue(service.contains("settlePlayerNewDay"));
        assertFalse(plan.contains(
                "case \"npc_friendship_daily\" -> atomic(name, () -> friendshipDaily(context))"));
        assertTrue(plan.contains("NpcFriendshipDailyService"));
        assertTrue(plan.contains(".createDailyWorkUnit("));
    }
}
