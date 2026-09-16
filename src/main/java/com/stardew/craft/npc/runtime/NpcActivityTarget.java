package com.stardew.craft.npc.runtime;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/** Optional existing scene surface used by a standing activity; never creates or changes blocks. */
public record NpcActivityTarget(BlockPos block,Vec3 surface) {
    public static boolean required(String point) {
        var p=NpcSupportTarget.point(point);return p!=null&&p.has("activity_target");
    }
    public static NpcActivityTarget resolve(ServerLevel level,String point) {
        var p=NpcSupportTarget.point(point);if(p==null||!p.has("activity_target"))return null;
        var t=p.getAsJsonObject("activity_target");var block=new BlockPos(t.get("x").getAsInt(),t.get("y").getAsInt(),t.get("z").getAsInt());
        if(!level.hasChunkAt(block)||level.getBlockState(block).isAir())return null;
        return new NpcActivityTarget(block,Vec3.atBottomCenterOf(block).add(0,t.get("surface_y").getAsDouble(),0));
    }
    public void write(CompoundTag event) {
        event.putLong("activityTarget",block.asLong());event.putDouble("targetX",surface.x);event.putDouble("targetY",surface.y);event.putDouble("targetZ",surface.z);
    }
    public static boolean unchanged(ServerLevel level,CompoundTag event) {
        if(!event.contains("activityTarget"))return !required(event.getString("point"));
        var target=resolve(level,event.getString("point"));
        return target!=null&&target.block.asLong()==event.getLong("activityTarget")
                &&target.surface.distanceToSqr(new Vec3(event.getDouble("targetX"),event.getDouble("targetY"),event.getDouble("targetZ")))<1e-8;
    }
}
