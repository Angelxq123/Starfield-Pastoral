package com.stardew.craft.time.settlement;

import com.stardew.craft.network.overnight.OvernightSettlementPayload;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class DailySettlementBarrier {
    private final Map<UUID, Integer> locks = new HashMap<>();
    private final Map<UUID, ReadyResult> ready = new HashMap<>();

    public void lockAll(int absoluteDay, Collection<UUID> playerIds) {
        lockAllScoped(absoluteDay, playerIds);
    }

    public LockScope lockAllScoped(int absoluteDay, Collection<UUID> playerIds) {
        if (absoluteDay <= 0) {
            throw new IllegalArgumentException("absoluteDay must be positive");
        }
        Objects.requireNonNull(playerIds, "playerIds");
        List<UUID> copiedIds = playerIds.stream()
                .map(playerId -> Objects.requireNonNull(playerId, "playerId"))
                .toList();

        Map<UUID, Integer> previousLocks = new HashMap<>();
        Map<UUID, ReadyResult> previousReady = new HashMap<>();
        for (UUID playerId : copiedIds) {
            Integer lockedDay = locks.get(playerId);
            if (lockedDay != null && lockedDay != absoluteDay) {
                throw new IllegalStateException(
                        "player is already locked for day " + lockedDay + ": " + playerId);
            }
        }

        Set<UUID> newlyLocked = new java.util.LinkedHashSet<>();
        for (UUID playerId : copiedIds) {
            Integer previousLock = locks.get(playerId);
            previousLocks.put(playerId, previousLock);
            previousReady.put(playerId, ready.get(playerId));
            if (previousLock == null) {
                newlyLocked.add(playerId);
            }
        }
        for (UUID playerId : copiedIds) {
            if (!locks.containsKey(playerId)) {
                ready.remove(playerId);
                locks.put(playerId, absoluteDay);
            }
        }
        return new LockScope(this, previousLocks, previousReady,
                newlyLocked);
    }

    public void rollback(LockScope scope) {
        Objects.requireNonNull(scope, "scope");
        if (scope.barrier() != this) {
            throw new IllegalArgumentException("lock scope belongs to another barrier");
        }
        scope.previousLocks().forEach((playerId, lockedDay) -> {
            if (lockedDay == null) {
                locks.remove(playerId);
            } else {
                locks.put(playerId, lockedDay);
            }
        });
        scope.previousReady().forEach((playerId, result) -> {
            if (result == null) {
                ready.remove(playerId);
            } else {
                ready.put(playerId, result);
            }
        });
    }

    public boolean isLocked(UUID playerId) {
        return locks.containsKey(Objects.requireNonNull(playerId, "playerId"));
    }

    public int lockedDay(UUID playerId) {
        return locks.getOrDefault(Objects.requireNonNull(playerId, "playerId"), -1);
    }

    public Set<UUID> lockedPlayerIds(int absoluteDay) {
        if (absoluteDay <= 0) {
            return Set.of();
        }
        return locks.entrySet().stream()
                .filter(entry -> entry.getValue() == absoluteDay)
                .map(Map.Entry::getKey)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
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

    public boolean publishReadyAll(int absoluteDay, Map<UUID, ReadyResult> results) {
        if (absoluteDay <= 0) {
            throw new IllegalArgumentException("absoluteDay must be positive");
        }
        Map<UUID, ReadyResult> copied = Map.copyOf(
                Objects.requireNonNull(results, "results"));
        for (Map.Entry<UUID, ReadyResult> entry : copied.entrySet()) {
            UUID playerId = entry.getKey();
            ReadyResult result = entry.getValue();
            Integer lockedDay = locks.get(playerId);
            ReadyResult existing = ready.get(playerId);
            if (result.absoluteDay() != absoluteDay
                    || lockedDay == null || lockedDay != absoluteDay
                    || (existing != null && existing != result)) {
                return false;
            }
        }
        for (Map.Entry<UUID, ReadyResult> entry : copied.entrySet()) {
            ready.putIfAbsent(entry.getKey(), entry.getValue());
        }
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

    public static record ReadyResult(
            int absoluteDay,
            OvernightSettlementPayload payload,
            boolean personalSettlement) {
        public ReadyResult(int absoluteDay, OvernightSettlementPayload payload) {
            this(absoluteDay, payload, true);
        }

        public static ReadyResult barrierOnly(int absoluteDay) {
            return new ReadyResult(
                    absoluteDay,
                    OvernightSettlementPayload.barrierOnly(absoluteDay),
                    false);
        }

        public boolean canAcknowledge(boolean personalSettlementCompleted) {
            return !personalSettlement || personalSettlementCompleted;
        }

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

    public static record LockScope(
            DailySettlementBarrier barrier,
            Map<UUID, Integer> previousLocks,
            Map<UUID, ReadyResult> previousReady,
            Set<UUID> newlyLocked) {
        public LockScope {
            Objects.requireNonNull(barrier, "barrier");
            previousLocks = Collections.unmodifiableMap(
                    new HashMap<>(Objects.requireNonNull(previousLocks, "previousLocks")));
            previousReady = Collections.unmodifiableMap(
                    new HashMap<>(Objects.requireNonNull(previousReady, "previousReady")));
            newlyLocked = Set.copyOf(Objects.requireNonNull(newlyLocked, "newlyLocked"));
        }
    }
}
