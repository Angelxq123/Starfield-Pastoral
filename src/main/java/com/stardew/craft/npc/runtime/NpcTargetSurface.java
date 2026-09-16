package com.stardew.craft.npc.runtime;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/** Outdoor surface policy only; exact indoor floors never go through a heightmap. */
final class NpcTargetSurface {
    private NpcTargetSurface() {}
    static Vec3 resolve(ServerLevel level,Vec3 point,boolean ground,boolean indoor) {
        if(point==null || !ground || indoor || level==null) return point;
        var column=BlockPos.containing(point);
        // Keep the authored column until the travel ticket loads it; the next resolution uses its surface.
        if(!level.hasChunkAt(column)) return point;
        int top=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,column.getX(),column.getZ());
        var support=new BlockPos(column.getX(),top-1,column.getZ());
        var block=level.getBlockState(support);
        if(!block.getFluidState().isEmpty()) return null;
        double y=Double.NEGATIVE_INFINITY;
        for(var box:block.getCollisionShape(level,support).toAabbs())
            if(point.x-support.getX()>=box.minX && point.x-support.getX()<=box.maxX
                    && point.z-support.getZ()>=box.minZ && point.z-support.getZ()<=box.maxZ)
                y=Math.max(y,support.getY()+box.maxY);
        return Double.isFinite(y)?new Vec3(point.x,y,point.z):null;
    }
}
