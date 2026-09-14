package com.stardew.craft.npc.runtime;

import com.google.gson.JsonObject;
import com.stardew.craft.npc.data.NpcDataRegistry;

/** Shared, reloadable navigation limits. Distances are Minecraft blocks, durations are ticks. */
public record NpcNavigationPolicy(int searchesPerTick, int retryTicks, int maxRetryTicks,
                                  double arrivalRadius, double verticalTolerance, int corridorRadius, int maxVisitedNodes, int searchRange) {
    private static long cachedRevision = -1;
    private static NpcNavigationPolicy cached;

    public static NpcNavigationPolicy current() {
        if (cachedRevision != NpcDataRegistry.revision()) {
            cached = decode(NpcDataRegistry.events().get("npc_runtime"));
            cachedRevision = NpcDataRegistry.revision();
        }
        return cached;
    }

    public static NpcNavigationPolicy decode(JsonObject root) {
        JsonObject navigation = root != null && root.has("navigation")
                ? root.getAsJsonObject("navigation") : new JsonObject();
        return new NpcNavigationPolicy(integer(navigation,"searches_per_tick",4,1,32),
                integer(navigation,"retry_ticks",20,1,200), integer(navigation,"max_retry_ticks",200,20,1200),
                number(navigation,"arrival_radius",.5,.1,1), number(navigation,"vertical_tolerance",.55,.05,.75),
                integer(navigation,"corridor_radius",1,0,2),integer(navigation,"max_visited_nodes",4096,256,16384),
                integer(navigation,"search_range",96,16,192));
    }

    public int retryDelay(int failures) {
        return (int)Math.min(maxRetryTicks, (long)retryTicks << Math.min(10,Math.max(0,failures-1)));
    }

    public boolean arrived(double dx, double dy, double dz, double radius) {
        return Double.isFinite(dx) && Double.isFinite(dy) && Double.isFinite(dz)
                && Math.abs(dy) <= verticalTolerance && dx*dx+dz*dz <= radius*radius;
    }

    private static int integer(JsonObject o,String key,int fallback,int min,int max) {
        double value = number(o,key,fallback,min,max);
        if (value != Math.rint(value)) throw new IllegalArgumentException("NPC navigation " + key + " must be an integer");
        return (int)value;
    }
    private static double number(JsonObject o,String key,double fallback,double min,double max) {
        if (!o.has(key)) return fallback;
        double value=o.get(key).getAsDouble();
        if (!Double.isFinite(value) || value<min || value>max)
            throw new IllegalArgumentException("NPC navigation " + key + " must be between " + min + " and " + max);
        return value;
    }
}
