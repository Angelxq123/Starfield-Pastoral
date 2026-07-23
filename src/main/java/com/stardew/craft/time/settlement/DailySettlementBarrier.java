package com.stardew.craft.time.settlement;

import com.stardew.craft.network.overnight.OvernightSettlementPayload;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class DailySettlementBarrier {
    private final Map<UUID, Integer> locks = new HashMap<>();
    private final Map<UUID, ReadyResult> ready = new HashMap<>();

    public void lockAll(int absoluteDay, Collection<UUID> playerIds) {
        if (absoluteDay <= 0) {
            throw new IllegalArgumentException("absoluteDay must be positive");
        }
        Objects.requireNonNull(playerIds, "playerIds");
        List<UUID> copiedIds = playerIds.stream()
                .map(playerId -> Objects.requireNonNull(playerId, "playerId"))
                .toList();

        for (UUID playerId : copiedIds) {
            Integer lockedDay = locks.get(playerId);
            if (lockedDay != null && lockedDay != absoluteDay) {
                throw new IllegalStateException(
                        "player is already locked for day " + lockedDay + ": " + playerId);
            }
        }

        for (UUID playerId : copiedIds) {
            if (!locks.containsKey(playerId)) {
                ready.remove(playerId);
                locks.put(playerId, absoluteDay);
            }
        }
    }

    public boolean isLocked(UUID playerId) {
        return locks.containsKey(Objects.requireNonNull(playerId, "playerId"));
    }

    public int lockedDay(UUID playerId) {
        return locks.getOrDefault(Objects.requireNonNull(playerId, "playerId"), -1);
    }

    public boolean canCancelSleep(UUID playerId) {
        return !isLocked(playerId);
    }

    public ReadyResult readyResult(UUID playerId, int absoluteDay) {
        ReadyResult result = ready.get(Objects.requireNonNull(playerId, "playerId"));
        return result != null && result.absoluteDay() == absoluteDay ? result : null;
    }

    public boolean publishReady(UUID playerId, ReadyResult result) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(result, "result");
        Integer lockedDay = locks.get(playerId);
        if (lockedDay == null || lockedDay != result.absoluteDay()) {
            return false;
        }

        ReadyResult existing = ready.get(playerId);
        if (existing != null) {
            return existing == result;
        }
        ready.put(playerId, result);
        return true;
    }

    public boolean replaceReady(UUID playerId, ReadyResult result) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(result, "result");
        Integer lockedDay = locks.get(playerId);
        ReadyResult existing = ready.get(playerId);
        if (lockedDay == null || lockedDay != result.absoluteDay()
                || existing == null || existing.absoluteDay() != result.absoluteDay()) {
            return false;
        }
        ready.put(playerId, result);
        return true;
    }

    public boolean acknowledge(UUID playerId, int absoluteDay) {
        Objects.requireNonNull(playerId, "playerId");
        Integer lockedDay = locks.get(playerId);
        ReadyResult result = ready.get(playerId);
        if (lockedDay == null || lockedDay != absoluteDay
                || result == null || result.absoluteDay() != absoluteDay) {
            return false;
        }
        locks.remove(playerId);
        ready.remove(playerId);
        return true;
    }

    public void clear() {
        locks.clear();
        ready.clear();
    }

    public static record ReadyResult(int absoluteDay, OvernightSettlementPayload payload) {
        public ReadyResult {
            Objects.requireNonNull(payload, "payload");
            if (absoluteDay <= 0) {
                throw new IllegalArgumentException("absoluteDay must be positive");
            }
            if (payload.absoluteDay() <= 0) {
                throw new IllegalArgumentException("payload absoluteDay must be positive");
            }
            if (payload.absoluteDay() != absoluteDay) {
                throw new IllegalArgumentException(
                        "payload absoluteDay must match ready result: " + payload.absoluteDay());
            }
        }
    }
}
