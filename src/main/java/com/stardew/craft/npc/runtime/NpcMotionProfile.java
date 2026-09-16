package com.stardew.craft.npc.runtime;

import com.google.gson.JsonObject;
import com.stardew.craft.npc.data.NpcDataRegistry;
import java.util.Map;

/** Physics values are authored per actor, independently from visual gait and demographic traits. */
public record NpcMotionProfile(float width,float height,float eyeHeight,double speed,double stepHeight,boolean attention) {
    /** World travel scale for the larger MC map; compensate visual stride by the same factor. */
    public static final double TRAVEL_SPEED_MULTIPLIER=1.5;

    public double travelSpeed() { return speed*TRAVEL_SPEED_MULTIPLIER; }

    private static long revision=-1;
    private static Map<String,NpcMotionProfile> profiles=Map.of();
    public static NpcMotionProfile forActor(String id) {
        if (revision!=NpcDataRegistry.revision()) {
            profiles=compile(NpcDataRegistry.events()); revision=NpcDataRegistry.revision();
        }
        return profiles.getOrDefault(id,defaults(id));
    }
    private static NpcMotionProfile defaults(String id) {
        return new NpcMotionProfile(.6F,1.8F,1.62F,.2,.6,
                com.stardew.craft.npc.data.NpcAnimationInspector.hasNativeAnimation(id));
    }
    public static Map<String,NpcMotionProfile> compile(Map<String,JsonObject> events) {
        var result=new java.util.LinkedHashMap<String,NpcMotionProfile>();
        for (var event:events.entrySet()) {
            if (!event.getKey().equals("npc_runtime") && !event.getKey().endsWith(":npc_runtime")) continue;
            if (!event.getValue().has("actors")) continue;
            for (var entry:event.getValue().getAsJsonObject("actors").entrySet()) {
                var o=entry.getValue().getAsJsonObject();
                var fallback=defaults(entry.getKey());
                float height=(float)number(o,"height",1.8,.5,3);
                var profile=new NpcMotionProfile((float)number(o,"width",.6,.2,2),height,
                        (float)number(o,"eye_height",height*.9,.2,height),number(o,"speed",.2,.05,.5),
                        number(o,"step_height",.6,.1,1),o.has("attention")?o.get("attention").getAsBoolean():fallback.attention());
                if (result.putIfAbsent(entry.getKey(),profile)!=null) throw new IllegalArgumentException("Duplicate NPC motion profile "+entry.getKey());
            }
        }
        return Map.copyOf(result);
    }
    private static double number(JsonObject o,String key,double fallback,double min,double max) {
        double value=o.has(key)?o.get(key).getAsDouble():fallback;
        if (!Double.isFinite(value) || value<min || value>max) throw new IllegalArgumentException("Invalid NPC motion "+key);
        return value;
    }
}
