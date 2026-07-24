package com.stardew.craft.time;

import com.stardew.craft.player.PlayerStardewData;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DailyMailRecoveryTest {

    @Test
    void frozenOfflineDateMailSurvivesCommitAndReconnectsExactlyOnce() {
        UUID frozenPlayerId = UUID.randomUUID();
        PlayerStardewData frozenPlayer = new PlayerStardewData(frozenPlayerId);
        PlayerStardewData lateLogin = new PlayerStardewData(UUID.randomUUID());
        int targetAbsoluteDay = 226;

        // Rollover commit runs after the frozen player logs out.
        StardewTimeManager.queueDateTriggeredMail(frozenPlayer, targetAbsoluteDay);
        PlayerStardewData restored = PlayerStardewData.fromNBT(
                frozenPlayer.toNBT(), frozenPlayerId);

        List<Integer> scheduledDays = new ArrayList<>();
        StardewTimeManager.resumePendingDateTriggeredMail(restored, scheduledDays::add);
        StardewTimeManager.resumePendingDateTriggeredMail(restored, scheduledDays::add);
        StardewTimeManager.resumePendingDateTriggeredMail(lateLogin, scheduledDays::add);

        assertEquals(List.of(targetAbsoluteDay), scheduledDays);
        assertEquals(List.of(), restored.getPendingDateTriggeredMailDays());
        assertEquals(List.of(), lateLogin.getPendingDateTriggeredMailDays());
    }
}
