package com.stardew.craft.npc.attention;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Server-tick accumulator. Candidate input must already pass the ray/visibility checks. */
public final class NpcStareTracker {
    public record Candidate(int id, double distanceSquared) {}
    private final Map<Integer,Integer> dwell = new HashMap<>();
    private long availableAt;

    public int tick(long tick, List<Candidate> candidates, boolean available) {
        if (!available || tick<availableAt) { dwell.clear(); return -1; }
        dwell.keySet().removeIf(id -> candidates.stream().noneMatch(c -> c.id==id));
        Candidate selected = null;
        for (var candidate : candidates) {
            int count = dwell.merge(candidate.id,1,Integer::sum);
            if (count<50) continue;
            if (selected==null || candidate.distanceSquared<selected.distanceSquared
                    || (candidate.distanceSquared==selected.distanceSquared && candidate.id<selected.id)) selected=candidate;
        }
        if (selected==null) return -1;
        dwell.clear();
        return selected.id;
    }
    public void coolDownUntil(long tick) { availableAt=tick; dwell.clear(); }
}
