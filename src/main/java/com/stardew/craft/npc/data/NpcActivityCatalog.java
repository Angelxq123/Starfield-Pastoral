package com.stardew.craft.npc.data;

import com.google.gson.JsonObject;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Data packs add activities through npc/events/* with event_id ending in npc_activities. */
public final class NpcActivityCatalog {
    private static long revision=-1;
    private static Map<String,NpcActivityDefinition> definitions=Map.of();
    private NpcActivityCatalog() {}

    public static NpcActivityDefinition find(String npc,String behavior) {
        refresh();
        for (var definition:definitions.values())
            if (definition.actors().contains(npc) && (definition.id().equals(behavior)
                    || definition.aliases().contains(behavior) || definition.asset().equals(behavior))) return definition;
        return null;
    }
    public static Map<String,NpcActivityDefinition> all() { refresh(); return definitions; }
    private static void refresh() {
        if (revision==NpcDataRegistry.revision()) return;
        definitions=compile(NpcDataRegistry.events()); revision=NpcDataRegistry.revision();
    }
    public static Map<String,NpcActivityDefinition> compile(Map<String,JsonObject> events) {
        Map<String,NpcActivityDefinition> result=new LinkedHashMap<>();
        Set<String> bindings=new LinkedHashSet<>();
        for (var event:events.entrySet()) {
            if (!event.getKey().equals("npc_activities") && !event.getKey().endsWith(":npc_activities")) continue;
            String namespace=event.getKey().contains(":")?event.getKey().split(":",2)[0]:"stardewcraft";
            var root=event.getValue().getAsJsonObject("activities");
            if (root==null) throw new IllegalArgumentException("Missing activities in " + event.getKey());
            for (var entry:root.entrySet()) {
                var o=entry.getValue().getAsJsonObject();
                String id=entry.getKey().contains(":")?entry.getKey():namespace+":"+entry.getKey();
                Set<String> actors=strings(o,"actors"),aliases=strings(o,"aliases");
                String prefix=text(o,"clip_prefix","");
                var d=new NpcActivityDefinition(id,actors,aliases,text(o,"asset",""),
                        text(o,"enter_clip",prefix+"_enter"),text(o,"play_clip",prefix+"_play"),
                        text(o,"exit_clip",prefix+"_exit"),o.get("enter_ticks").getAsBigDecimal().intValueExact(),o.get("exit_ticks").getAsBigDecimal().intValueExact(),text(o,"support",""),
                        text(o,"transition",!prefix.isBlank() || o.has("enter_clip") || o.has("exit_clip") || !text(o,"support","").isBlank()?"clips":"blend"));
                if (result.putIfAbsent(id,d)!=null) throw new IllegalArgumentException("Duplicate activity " + id);
                Set<String> names=new LinkedHashSet<>(aliases);names.add(id);names.add(d.asset());
                for (String actor:actors) for (String name:names)
                    if (!bindings.add(actor+"/"+name)) throw new IllegalArgumentException("Ambiguous activity " + actor+"/"+name);
            }
        }
        return java.util.Collections.unmodifiableMap(result);
    }
    private static String text(JsonObject o,String key,String fallback) { return o.has(key)?o.get(key).getAsString():fallback; }
    private static Set<String> strings(JsonObject o,String key) {
        Set<String> result=new LinkedHashSet<>();
        if (o.has(key)) for (var v:o.getAsJsonArray(key)) result.add(v.getAsString());
        return result;
    }
}
