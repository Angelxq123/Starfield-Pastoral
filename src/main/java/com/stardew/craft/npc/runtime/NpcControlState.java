package com.stardew.craft.npc.runtime;

import java.util.UUID;

/** One actor's execution lease. A generation makes previously issued work revocable. */
public final class NpcControlState {
    private String owner="";
    private int priority;
    private long expires;
    private long generation;
    private UUID entity;

    public long claim(String requested,int rank,long now,long duration,UUID boundEntity) {
        if (requested==null || requested.isBlank() || duration<1) return -1;
        if (!boundEntity.equals(entity)) { owner=""; entity=boundEntity; generation++; }
        if (!owner.isEmpty() && now<expires && !owner.equals(requested) && rank<=priority) return -1;
        if (!owner.equals(requested) || now>=expires) generation++;
        owner=requested; priority=rank; expires=now+duration;
        return generation;
    }
    public boolean boundTo(UUID id) { return id.equals(entity); }
    public boolean owns(String requested,long token,long now) {
        return token==generation && now<expires && owner.equals(requested);
    }
    public void release(String requested) {
        if (owner.equals(requested)) { owner=""; generation++; }
    }
    public String owner(long now) { return now<expires ? owner : ""; }
    public void cancel() { owner=""; generation++; }
}
