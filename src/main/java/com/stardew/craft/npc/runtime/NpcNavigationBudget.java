package com.stardew.craft.npc.runtime;

import net.minecraft.server.MinecraftServer;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/** FIFO admission for expensive local path searches, shared by ordinary and scripted NPCs. */
public final class NpcNavigationBudget {
    private static final Map<MinecraftServer,NpcNavigationBudget> SERVERS=new IdentityHashMap<>();
    private final LinkedHashMap<String,Long> waiting=new LinkedHashMap<>();
    private long tick=Long.MIN_VALUE;
    private int used;

    public static boolean acquire(MinecraftServer server,String request) {
        return SERVERS.computeIfAbsent(server,ignored->new NpcNavigationBudget())
                .acquire(request,server.getTickCount(),NpcNavigationPolicy.current().searchesPerTick());
    }

    boolean acquire(String request,long now,int limit) {
        if (tick!=now) {
            tick=now; used=0;
            // Active callers renew every tick. A departed requester cannot hold the queue for seconds.
            waiting.entrySet().removeIf(e->now-e.getValue()>1);
        }
        waiting.put(request,now);
        if (used>=limit || !waiting.keySet().iterator().next().equals(request)) return false;
        waiting.remove(request); used++; return true;
    }

    public static void clear(MinecraftServer server) { SERVERS.remove(server); }
    public static void cancel(MinecraftServer server,String request) {
        var budget=SERVERS.get(server);
        if(budget!=null) budget.waiting.remove(request);
    }
}
