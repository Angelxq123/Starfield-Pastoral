package com.stardew.craft.npc.runtime;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class NpcSpawnWorkQueue {
    private final ArrayDeque<String> pending = new ArrayDeque<>();
    private final Set<String> pendingIds = new HashSet<>();

    void enqueueScheduled(Collection<String> npcIds) {
        for (String npcId : npcIds) {
            if (pendingIds.add(npcId)) {
                pending.addLast(npcId);
            }
        }
    }

    void prioritize(Collection<String> npcIds) {
        List<String> priority = new ArrayList<>(new java.util.LinkedHashSet<>(npcIds));
        for (int i = priority.size() - 1; i >= 0; i--) {
            String npcId = priority.get(i);
            if (pendingIds.add(npcId)) {
                pending.addFirst(npcId);
            } else {
                pending.remove(npcId);
                pending.addFirst(npcId);
            }
        }
    }

    List<String> drain(int limit) {
        if (limit <= 0) {
            return List.of();
        }
        List<String> batch = new ArrayList<>(Math.min(limit, pending.size()));
        while (batch.size() < limit && !pending.isEmpty()) {
            String npcId = pending.removeFirst();
            pendingIds.remove(npcId);
            batch.add(npcId);
        }
        return batch;
    }

    int pendingCount() {
        return pending.size();
    }

    void clear() {
        pending.clear();
        pendingIds.clear();
    }
}
