package com.stardew.craft.npc.runtime;

import com.google.gson.JsonObject;
import com.stardew.craft.npc.data.NpcScheduleCompiler;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** Compiled day tasks are shared across actors/ticks and discarded when source snapshots change. */
final class NpcSchedulePlans {
    private static final Map<JsonObject,List<NpcScheduleCompiler.Node>> PLANS=new WeakHashMap<>();
    static List<NpcScheduleCompiler.Node> nodes(JsonObject day) {
        return PLANS.computeIfAbsent(day,source->{
            JsonObject wrapper=new JsonObject(); wrapper.add("day",source);
            return NpcScheduleCompiler.compile(wrapper).get("day");
        });
    }
    static void clear() { PLANS.clear(); }
}
