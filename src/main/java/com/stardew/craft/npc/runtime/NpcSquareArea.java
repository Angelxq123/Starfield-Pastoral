package com.stardew.craft.npc.runtime;

import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Explicit MC feet-cell bounds; SDV square dimensions never imply a map conversion. */
public record NpcSquareArea(BlockPos min, BlockPos max) {
    public static NpcSquareArea decode(JsonObject value) {
        var min=corner(value,"min");var max=corner(value,"max");
        long x=(long)max.getX()-min.getX()+1,y=(long)max.getY()-min.getY()+1,z=(long)max.getZ()-min.getZ()+1;
        if(x<1||y<1||z<1||x>32||z>32||y>4||x*y*z>4096)
            throw new IllegalArgumentException("square_area requires ordered bounds within 32x4x32 feet cells");
        return new NpcSquareArea(min,max);
    }
    private static BlockPos corner(JsonObject value,String key) {
        var a=value.getAsJsonArray(key);
        if(a==null||a.size()!=3)throw new IllegalArgumentException("square_area "+key+" requires three integer coordinates");
        return new BlockPos(a.get(0).getAsBigDecimal().intValueExact(),a.get(1).getAsBigDecimal().intValueExact(),a.get(2).getAsBigDecimal().intValueExact());
    }
    public AABB bounds(){return new AABB(min.getX(),min.getY(),min.getZ(),max.getX()+1.,max.getY()+1.,max.getZ()+1.);}
    public boolean contains(Vec3 feet,double halfWidth) {
        return feet.x-halfWidth>=min.getX()-1e-6&&feet.x+halfWidth<=max.getX()+1.+1e-6
                &&feet.z-halfWidth>=min.getZ()-1e-6&&feet.z+halfWidth<=max.getZ()+1.+1e-6
                &&feet.y>=min.getY()-.01&&feet.y<max.getY()+1.;
    }
    public static NpcSquareArea forPoint(String point) {
        var p=com.stardew.craft.npc.data.NpcRoutePoints.get(point);
        return p!=null&&p.has("square_area")?decode(p.getAsJsonObject("square_area")):null;
    }
}
