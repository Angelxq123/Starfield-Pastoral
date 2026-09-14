package com.stardew.craft.npc.animation;

import com.stardew.craft.entity.npc.StardewNpcEntity;

/** Compatibility name retained for integrations; ordinary entities use NpcScheduleActivity. */
public final class SamScheduleActivity {
    private final NpcScheduleActivity delegate;
    public SamScheduleActivity(StardewNpcEntity npc) { delegate=new NpcScheduleActivity(npc); }
    public boolean isSettling() { return delegate.isSettling(); }
    public boolean hasPendingInteraction() { return delegate.hasPendingInteraction(); }
    public void interrupt(Runnable callback) { delegate.interrupt(callback); }
    public void tick() { delegate.tick(); }
}
