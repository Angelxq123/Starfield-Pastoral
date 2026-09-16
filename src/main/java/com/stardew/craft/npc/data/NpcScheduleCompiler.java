package com.stardew.craft.npc.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Compiles both legacy schedule strings and structured data into immutable ordered tasks. */
public final class NpcScheduleCompiler {
    public record Node(int time,String location,String point,int tileX,int tileY,int facing,String behavior,int index) {}
    private NpcScheduleCompiler() {}

    public static Map<String,List<Node>> compile(JsonObject root) {
        com.stardew.craft.npc.runtime.NpcScheduleRules.validate(root);
        validateGoto(root);
        Map<String,List<Node>> result=new LinkedHashMap<>();
        for (var day:root.entrySet()) {
            if (day.getKey().startsWith("_") || !day.getValue().isJsonObject()) continue;
            List<Map.Entry<String,JsonElement>> entries=day.getValue().getAsJsonObject().entrySet().stream()
                    .filter(e->!e.getKey().startsWith("_"))
                    .sorted(Comparator.comparingInt(e->time(e.getKey()))).toList();
            List<Node> nodes=new ArrayList<>();
            String previous="";
            for (var entry:entries) {
                int checkpoint=time(entry.getKey());
                if (checkpoint<0) throw new IllegalArgumentException("Invalid schedule time " + day.getKey()+"/"+entry.getKey());
                Node node=parse(checkpoint,entry.getValue(),previous,nodes.size());
                nodes.add(node); previous=node.location();
            }
            result.put(day.getKey(),List.copyOf(nodes));
        }
        return Map.copyOf(result);
    }

    private static void validateGoto(JsonObject root) {
        for (String key:root.keySet()) {
            if (key.startsWith("_") || !root.get(key).isJsonObject()) continue;
            var visited=new java.util.HashSet<String>();
            String next=key;
            while (next!=null) {
                String requested=next;
                next=root.keySet().stream().filter(k->k.equalsIgnoreCase(requested)).findFirst().orElse(null);
                if (next==null || !root.get(next).isJsonObject()) throw new IllegalArgumentException("Missing GOTO: "+requested);
                if (!visited.add(next.toLowerCase(java.util.Locale.ROOT))) throw new IllegalArgumentException("Schedule GOTO cycle: "+key);
                var day=root.getAsJsonObject(next);
                next=day.has("_goto") ? day.get("_goto").getAsString() : null;
            }
        }
    }

    public static int time(String key) {
        try {
            int value=Integer.parseInt(key);
            return value>=0 && value<=2959 && value%100<60 ? value : -1;
        } catch (NumberFormatException ignored) { return -1; }
    }

    private static Node parse(int time,JsonElement value,String previous,int index) {
        if (value.isJsonObject()) {
            JsonObject o=value.getAsJsonObject();
            String location=o.has("location")?o.get("location").getAsString():previous;
            String point=o.has("point")?o.get("point").getAsString():"";
            int x=o.has("tile_x")?o.get("tile_x").getAsBigDecimal().intValueExact():0;
            int y=o.has("tile_y")?o.get("tile_y").getAsBigDecimal().intValueExact():0;
            return node(time,location,point,x,y,o.has("facing")?o.get("facing").getAsBigDecimal().intValueExact():2,
                    o.has("behavior")?o.get("behavior").getAsString():"",index);
        }
        String[] parts=value.getAsString().trim().split("\\s+");
        int i=0;
        String location=integer(parts[0])?previous:parts[i++];
        if (i>=parts.length) throw new IllegalArgumentException("Schedule target missing: " + value);
        String point="";
        int x=0,y=0,facing=2;
        if (parts[i].startsWith("@")) {
            point=parts[i++].substring(1);
            if (point.isBlank()) throw new IllegalArgumentException("Empty schedule point");
            if (i<parts.length && integer(parts[i])) facing=Integer.parseInt(parts[i++]);
        } else if (parts.length-i==1 && integer(parts[i])) {
            facing=Integer.parseInt(parts[i++]);
        } else {
            if (parts.length-i<3) throw new IllegalArgumentException("Invalid schedule target: " + value);
            x=Integer.parseInt(parts[i++]); y=Integer.parseInt(parts[i++]); facing=Integer.parseInt(parts[i++]);
        }
        String behavior=i<parts.length?parts[i]:"";
        return node(time,location,point,x,y,facing,behavior,index);
    }

    private static Node node(int time,String location,String point,int x,int y,int facing,String behavior,int index) {
        if (location.isBlank() || facing<0 || facing>3) throw new IllegalArgumentException("Invalid schedule location/facing");
        return new Node(time,location,point,x,y,facing,behavior,index);
    }
    private static boolean integer(String s) {
        try { Integer.parseInt(s); return true; } catch (NumberFormatException ignored) { return false; }
    }
}
