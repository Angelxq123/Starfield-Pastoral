package com.stardew.craft.npc.data;

import com.google.gson.JsonObject;
import java.util.Map;
import java.util.Set;

/** Shape checks run before publishing a reload; missing map points remain recoverable at runtime. */
public final class NpcRouteDataValidation {
    private NpcRouteDataValidation() {}

    public static void validate(Map<String,JsonObject> events) {
        var defaults=events.get("default_spawns");
        if (defaults!=null && defaults.has("spawns")) {
            for(var entry:defaults.getAsJsonObject("spawns").entrySet()) {
                var spawn=entry.getValue().getAsJsonObject();
                String context="default_spawns."+entry.getKey();
                text(spawn,"point",context,false);
                if(spawn.has("retired_positions")) for(var old:spawn.getAsJsonArray("retired_positions")) {
                    var position=old.getAsJsonObject();
                    for(String axis:Set.of("x","y","z")) {
                        if(!position.has(axis) || !Double.isFinite(position.get(axis).getAsDouble()))
                            throw new IllegalArgumentException(context+".retired_positions: finite XYZ required");
                    }
                }
            }
        }
        var graph=events.get("location_graph");
        if(graph!=null) {
            try {
                var edges=graph.getAsJsonArray("edges");
                if(edges==null) throw new IllegalArgumentException("Missing edges");
                int index=0;
                for(var entry:edges) {
                    var edge=entry.getAsJsonObject();
                    String context="location_graph.edges["+(index++)+"]";
                    text(edge,"from",context,true);text(edge,"to",context,true);
                    for(String key:Set.of("via_outdoor","via_indoor","reverse_via_outdoor","reverse_via_indoor"))
                        text(edge,key,context,false);
                    mode(edge,Set.of("walk","warp","walk_warp"),context);
                }
            } catch(RuntimeException error) {throw new IllegalArgumentException("location_graph: "+error.getMessage(),error);}
        }
        var routes=events.get("npc_route_profiles");
        if(routes!=null) {
            try {
                var profiles=routes.getAsJsonObject("profiles");
                if(profiles==null) throw new IllegalArgumentException("Missing profiles");
                for(var npc:profiles.entrySet()) for(var location:npc.getValue().getAsJsonObject().entrySet()) {
                    int index=0;
                    for(var entry:location.getValue().getAsJsonArray()) {
                        var step=entry.getAsJsonObject();
                        String context=npc.getKey()+"/"+location.getKey()+"["+(index++)+"]";
                        text(step,"point",context,true);mode(step,Set.of("walk","warp"),context);
                    }
                }
            } catch(RuntimeException error) {throw new IllegalArgumentException("npc_route_profiles: "+error.getMessage(),error);}
        }
    }

    private static String text(JsonObject object,String key,String context,boolean required) {
        if(!object.has(key) && !required) return "";
        var value=object.get(key);
        if(value==null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()
                || required && value.getAsString().isBlank())
            throw new IllegalArgumentException(context+"."+key+": expected "+(required?"nonempty ":"")+"string");
        return value.getAsString().trim();
    }
    private static void mode(JsonObject object,Set<String> supported,String context) {
        String mode=text(object,"mode",context,false).toLowerCase(java.util.Locale.ROOT);
        if(!mode.isEmpty() && !supported.contains(mode)) throw new IllegalArgumentException(context+".mode: "+mode);
    }
}
