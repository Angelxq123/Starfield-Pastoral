package com.stardew.craft.player;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerDataSyncSnapshotCacheTest {
    @Test
    void suppressesOnlyAnIdenticalSnapshotForTheSamePlayer() {
        PlayerDataSyncSnapshotCache cache = new PlayerDataSyncSnapshotCache();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        CompoundTag snapshot = snapshot(100, "hat_1");

        assertTrue(cache.shouldSend(first, snapshot));
        assertFalse(cache.shouldSend(first, snapshot.copy()));
        assertTrue(cache.shouldSend(first, snapshot(101, "hat_1")));
        assertTrue(cache.shouldSend(second, snapshot));
    }

    @Test
    void storesADefensiveCopyAndCanBeCleared() {
        PlayerDataSyncSnapshotCache cache = new PlayerDataSyncSnapshotCache();
        UUID player = UUID.randomUUID();
        CompoundTag first = snapshot(100, "hat_1");

        assertTrue(cache.shouldSend(player, first));
        first.putInt("Money", 999);
        assertFalse(cache.shouldSend(player, snapshot(100, "hat_1")));

        cache.clear(player);
        assertTrue(cache.shouldSend(player, snapshot(100, "hat_1")));
        cache.clearAll();
        assertTrue(cache.shouldSend(player, snapshot(100, "hat_1")));
    }

    private static CompoundTag snapshot(int money, String hat) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Money", money);
        tag.putString("Hat", hat);
        return tag;
    }
}
