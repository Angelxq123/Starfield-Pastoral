package com.stardew.craft.npc.runtime;

import java.util.List;

/** Departure-time queue. A later due task cannot replace a route still in flight. */
public final class NpcScheduleCursor {
    private int current = -1;
    private int lastClock = -1;

    public void resume(int index,int clock) { current=index; lastClock=clock; }
    public void skipToDue(List<Integer> times,int clock) {
        while(current+1<times.size() && times.get(current+1)<=clock) current++;
        lastClock=clock;
    }

    public int select(List<Integer> times, int clock, boolean arrived) {
        if (times.isEmpty()) return -1;
        if (current < 0 || clock < lastClock || current >= times.size()) {
            current = -1;
            // First observation/recovery catches up; subsequent ticks preserve every due stop.
            while (current+1 < times.size() && times.get(current+1) <= clock) current++;
        } else if (arrived && current+1 < times.size() && times.get(current+1) <= clock) {
            current++;
        }
        lastClock = clock;
        return current;
    }
}
