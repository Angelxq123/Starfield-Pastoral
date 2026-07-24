package com.stardew.craft.specialorder;

import com.stardew.craft.player.PlayerStardewData;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

    @Test
    void temporaryItemRemovalFailureRetainsCleanupForLoginRetry() {
        PlayerStardewData data = new PlayerStardewData(UUID.randomUUID());
        SpecialOrderManager.queueTemporaryItemCleanup(
                data, "stardewcraft:prismatic_jelly");
        List<String> removed = new ArrayList<>();

        assertThrows(IllegalStateException.class, () ->
                SpecialOrderManager.resumeTemporaryItemCleanup(data, itemId -> {
                    throw new IllegalStateException("injected inventory failure");
                }));
        assertEquals(List.of("stardewcraft:prismatic_jelly"),
                data.getPendingSpecialOrderItemCleanups());

        SpecialOrderManager.resumeTemporaryItemCleanup(data, removed::add);
        SpecialOrderManager.resumeTemporaryItemCleanup(data, removed::add);

        assertEquals(List.of("stardewcraft:prismatic_jelly"), removed);
        assertEquals(List.of(), data.getPendingSpecialOrderItemCleanups());
    }
}
