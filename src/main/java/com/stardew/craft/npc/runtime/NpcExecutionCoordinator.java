package com.stardew.craft.npc.runtime;

import com.stardew.craft.entity.npc.StardewNpcEntity;
import net.minecraft.server.MinecraftServer;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

/** Common authority boundary for schedule movement, activities and authored scene controllers. */
public final class NpcExecutionCoordinator {
    public static final String SCHEDULE="stardewcraft:schedule", ACTIVITY="stardewcraft:activity",
            DIALOGUE="stardewcraft:dialogue";
    public static boolean autonomous(StardewNpcEntity npc) {
        if (com.stardew.craft.festival.FestivalNpcController.controlsNpc(npc.getNpcId())) return false;
        String owner=owner(npc);
        return owner.isEmpty() || owner.equals(SCHEDULE) || owner.equals(ACTIVITY) || owner.equals(DIALOGUE);
    }
    private static final Map<MinecraftServer,Map<String,NpcControlState>> SERVERS=new IdentityHashMap<>();
    private NpcExecutionCoordinator() {}

    public static long claim(StardewNpcEntity npc,String owner,int priority,int duration) {
        if (npc.level().isClientSide || npc.isRemoved()) return -1;
        var control=state(npc);
        String previous=control.owner(now(npc));
        long token=control.claim(owner,priority,now(npc),duration,npc.getUUID());
        if(token>=0 && !owner.equals(previous)) {
            npc.getNavigation().stop();
            npc.getMoveControl().setWantedPosition(npc.getX(),npc.getY(),npc.getZ(),0);
            npc.setSpeed(0); npc.setZza(0); npc.setXxa(0);
            npc.setDeltaMovement(0,npc.getDeltaMovement().y,0);
            if(npc.level() instanceof net.minecraft.server.level.ServerLevel level) {
                NpcNavigationBudget.cancel(level.getServer(),level.dimension().location()+"/"+npc.getNpcId());
                NpcChunkForceManager.releaseRouteCorridor(level,npc.getNpcId());
            }
            if(!owner.equals(SCHEDULE) && !owner.equals(ACTIVITY) && !owner.equals(DIALOGUE))
                npc.cancelAutonomousActions();
        }
        return token;
    }
    public static void release(StardewNpcEntity npc,String owner) { if (!npc.level().isClientSide && state(npc).boundTo(npc.getUUID())) state(npc).release(owner); }
    public static String owner(StardewNpcEntity npc) { return npc.level().isClientSide ? "" : state(npc).owner(now(npc)); }
    public static boolean owns(StardewNpcEntity npc,String owner,long token) {
        return !npc.level().isClientSide && state(npc).boundTo(npc.getUUID()) && state(npc).owns(owner,token,now(npc));
    }
    public static void cancel(StardewNpcEntity npc) { if (!npc.level().isClientSide && state(npc).boundTo(npc.getUUID())) state(npc).cancel(); }
    public static void clear(MinecraftServer server) { SERVERS.remove(server); NpcNavigationBudget.clear(server); }
    private static long now(StardewNpcEntity npc) { return npc.getServer().getTickCount(); }
    private static NpcControlState state(StardewNpcEntity npc) {
        return SERVERS.computeIfAbsent(npc.getServer(),ignored->new HashMap<>())
                .computeIfAbsent(npc.getNpcId(),ignored->new NpcControlState());
    }
}
