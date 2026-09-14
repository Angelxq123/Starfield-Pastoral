package com.stardew.craft.npc.runtime;

import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/** Compatibility adapter for existing callers. New runtime code uses NpcSupportTarget. */
public record SamSupportTarget(BlockPos block, Vec3 origin, Vec3 approach, float yaw, float approachYaw) {
    public static JsonObject point(String id) { return NpcSupportTarget.point(id); }
    public static SamSupportTarget resolve(ServerLevel level, String id) {
        var target = NpcSupportTarget.resolve(level,id);
        return target == null ? null : new SamSupportTarget(target.block(),target.origin(),target.approach(),target.yaw(),target.approachYaw());
    }
    public static Vec3 rotate(Vec3 local,float yaw) { return NpcSupportTarget.rotate(local,yaw); }
    public static boolean occupied(ServerLevel level,BlockPos main) { return NpcSupportTarget.occupied(level,main); }
    public static Vec3 routePosition(ServerLevel level,String id,Vec3 fallback) { return NpcSupportTarget.routePosition(level,id,fallback); }
}
