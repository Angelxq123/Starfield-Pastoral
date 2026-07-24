package com.stardew.craft.specialorder;

import com.stardew.craft.player.PlayerStardewData;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SpecialOrderCleanupRecoveryTest {

    @Test
    void frozenOfflineTemporaryItemCleanupRunsOnReconnectWithoutTouchingLateLogin() {
        UUID frozenPlayerId = UUID.randomUUID();
        PlayerStardewData frozenPlayer = new PlayerStardewData(frozenPlayerId);
        PlayerStardewData lateLogin = new PlayerStardewData(UUID.randomUUID());

        // The order fails at commit after the frozen player logs out.
        SpecialOrderManager.queueTemporaryItemCleanup(
                frozenPlayer, "stardewcraft:ectoplasm");
        PlayerStardewData restored = PlayerStardewData.fromNBT(
                frozenPlayer.toNBT(), frozenPlayerId);

        List<String> frozenInventory = new ArrayList<>(List.of(
                "stardewcraft:ectoplasm", "minecraft:stone"));
        List<String> lateLoginInventory = new ArrayList<>(List.of(
                "stardewcraft:ectoplasm"));
        SpecialOrderManager.resumeTemporaryItemCleanup(restored, frozenInventory::remove);
        SpecialOrderManager.resumeTemporaryItemCleanup(restored, frozenInventory::remove);
        SpecialOrderManager.resumeTemporaryItemCleanup(lateLogin, lateLoginInventory::remove);

        assertEquals(List.of("minecraft:stone"), frozenInventory);
        assertEquals(List.of("stardewcraft:ectoplasm"), lateLoginInventory);
        assertEquals(List.of(), restored.getPendingSpecialOrderItemCleanups());
        assertEquals(List.of(), lateLogin.getPendingSpecialOrderItemCleanups());
    }
}
