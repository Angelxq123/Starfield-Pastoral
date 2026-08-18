package com.stardew.craft.player;

import net.minecraft.nbt.CompoundTag;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Remembers the last complete payload sent to each connected player. */
final class PlayerDataSyncSnapshotCache {
    private final Map<UUID, CompoundTag> snapshots = new HashMap<>();

    boolean shouldSend(UUID playerId, CompoundTag snapshot) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(snapshot, "snapshot");
        CompoundTag previous = snapshots.get(playerId);
        if (snapshot.equals(previous)) {
            return false;
        }
        snapshots.put(playerId, snapshot.copy());
        return true;
    }

    void clear(UUID playerId) {
        snapshots.remove(Objects.requireNonNull(playerId, "playerId"));
    }

    void clearAll() {
        snapshots.clear();
    }
}
