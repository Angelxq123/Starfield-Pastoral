package com.stardew.craft.npc.runtime;

import com.google.gson.JsonObject;
import com.stardew.craft.npc.data.NpcDataRegistry;
import java.util.HashMap;
import java.util.Map;

/** Explicit blocked tasks. Default waits; skipping overdue work requires a data-pack decision. */
public final class NpcTravelStatus {
    public record Policy(String action,int timeoutTicks) {}
    public record Blockage(String task,String reason,long since) {}
    private static final Map<String,Blockage> BLOCKED=new HashMap<>();
    private static long nextSummaryCheck,nextWarning;
    private static String lastSummary="";
    private static long revision=-1;
    private static Policy policy=new Policy("wait",1200);
    private NpcTravelStatus() {}
    public static Policy decode(JsonObject root) {
        var navigation=root==null?null:root.getAsJsonObject("navigation");
        if(navigation==null) return new Policy("wait",1200);
        String action=navigation.has("blocked_task_policy")?navigation.get("blocked_task_policy").getAsString():"wait";
        int ticks=navigation.has("blocked_task_ticks")?navigation.get("blocked_task_ticks").getAsBigDecimal().intValueExact():1200;
        if(!java.util.Set.of("wait","skip_to_due").contains(action) || ticks<200 || ticks>72000)
            throw new IllegalArgumentException("Invalid blocked-task policy or timeout (200..72000 ticks)");
        return new Policy(action,ticks);
    }
    public static void observe(String npc,String task,String reason,long now) {
        if(reason==null || reason.isBlank()) {BLOCKED.remove(npc);return;}
        var old=BLOCKED.get(npc);
        if(old==null || !old.task.equals(task) || !old.reason.equals(reason)) {
            BLOCKED.put(npc,new Blockage(task,reason,old!=null && old.task.equals(task)?old.since:now));
            com.mojang.logging.LogUtils.getLogger().debug("[NPC_BLOCKED] npc={} task={} reason={}",npc,task,reason);
        }
    }
    public static Blockage get(String npc) {return BLOCKED.get(npc);}
    public static boolean maySkip(String npc,long now) {
        if(revision!=NpcDataRegistry.revision()) {policy=decode(NpcDataRegistry.events().get("npc_runtime"));revision=NpcDataRegistry.revision();}
        var blocked=BLOCKED.get(npc);
        return blocked!=null && policy.action.equals("skip_to_due") && now-blocked.since>=policy.timeoutTicks;
    }
    public static void clear(String npc) {BLOCKED.remove(npc);}
    /** Brief failures are ordinary retries; warn only about changed, sustained blockage, as one batch. */
    public static void logSummary(long now) {
        if(now<nextSummaryCheck) return;
        nextSummaryCheck=now+200;
        var persistent=new java.util.TreeMap<String,Blockage>();
        BLOCKED.forEach((npc,state)->{if(now-state.since>=200) persistent.put(npc,state);});
        if(persistent.isEmpty()) {lastSummary="";return;}
        if(now<nextWarning) return;
        String signature=persistent.toString();
        if(signature.equals(lastSummary)) return;
        var reasons=new java.util.TreeMap<String,Integer>();
        persistent.values().forEach(state->reasons.merge(state.reason,1,Integer::sum));
        com.mojang.logging.LogUtils.getLogger().warn("[NPC_BLOCKED] {} actors blocked for >=200 ticks; reasons={}; sample={}. Inspect NPC state or enable DEBUG for details.",
                persistent.size(),reasons,persistent.keySet().stream().limit(8).toList());
        lastSummary=signature;
        nextWarning=now+600;
    }
    public static void clear() {BLOCKED.clear();nextSummaryCheck=0;nextWarning=0;lastSummary="";}
}
