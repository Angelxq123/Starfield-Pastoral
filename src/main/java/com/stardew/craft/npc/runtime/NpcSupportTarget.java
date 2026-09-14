package com.stardew.craft.npc.runtime;

import com.google.gson.JsonObject;
import com.stardew.craft.block.decor.BedDecorBlock;
import com.stardew.craft.block.utility.DyeableChairBlock;
import com.stardew.craft.block.utility.SofaBlock;
import com.stardew.craft.npc.data.NpcDataRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

/** Furniture coordinates identify a block; pathfinding ends beside it, never inside it. */
public record NpcSupportTarget(BlockPos block, Vec3 origin, Vec3 approach, float yaw, float approachYaw) {
    public static JsonObject point(String id) {
        return com.stardew.craft.npc.data.NpcRoutePoints.get(id);
    }

    public static NpcSupportTarget resolve(ServerLevel level, String id) {
        var point = point(id);
        if (point == null || !point.has("furniture")) return null;
        var anchor = com.stardew.craft.api.v1.world.StardewWorldAnchors.resolve(id);
        if (anchor.isPresent() && !anchor.get().dimension().equals(level.dimension().location())) return null;
        if(anchor.isEmpty() && (!point.has("x") || !point.has("y") || !point.has("z"))) return null;
        var pos = anchor.isPresent() ? BlockPos.containing(anchor.get().position())
                : BlockPos.containing(point.get("x").getAsDouble(),point.get("y").getAsDouble(),point.get("z").getAsDouble());
        if (!level.hasChunkAt(pos)) return null;
        var provided=com.stardew.craft.api.v1.internal.npc.StardewNpcExecutionRegistry.support(
                new com.stardew.craft.api.v1.npc.StardewNpcExecution.SupportContext(level,id,pos,point.get("furniture").getAsString()));
        if (provided.isPresent()) {
            var p=provided.get();
            return new NpcSupportTarget(p.block(),p.origin(),p.approach(),p.yaw(),p.approachYaw());
        }
        var state = level.getBlockState(pos);
        var selected=pos;
        boolean bed = point.get("furniture").getAsString().equals("bed");
        if (point.get("furniture").getAsString().equals("chair") && state.getBlock() instanceof SofaBlock) {
            var facing=state.getValue(SofaBlock.FACING);
            Vec3 center=Vec3.atBottomCenterOf(pos);
            if (point.has("seat_span")) {
                var step=span(point);
                if (step==null || step.getX()*facing.getStepX()+step.getZ()*facing.getStepZ()!=0) return null;
                var other=pos.offset(step);
                if (!level.hasChunkAt(other)) return null;
                var neighbor=level.getBlockState(other);
                if (!(neighbor.getBlock() instanceof SofaBlock) || neighbor.getValue(SofaBlock.FACING)!=facing) return null;
                center=center.add(Vec3.atLowerCornerOf(step).scale(.5));
            }
            float yaw=facing.toYRot();
            var origin=center.add(rotate(vector(point,"origin_offset",Vec3.ZERO),yaw));
            return new NpcSupportTarget(pos,origin,
                    origin.add(rotate(vector(point,"approach_offset",new Vec3(0,0,-1)),yaw)),yaw,yaw);
        }
        if (bed && state.getBlock() instanceof BedDecorBlock block) pos = block.resolveMainPos(level,pos,state);
        else if (point.get("furniture").getAsString().equals("chair") && state.getBlock() instanceof DyeableChairBlock block) pos = block.resolveMainPos(level,pos,state);
        else if (point.get("furniture").getAsString().equals("chair") && state.getBlock() instanceof com.stardew.craft.block.utility.ChairBlock block) pos = block.resolveMainPos(level,pos,state);
        else return null;
        state = level.getBlockState(pos);
        float yaw = state.getValue(BlockStateProperties.HORIZONTAL_FACING).toYRot();
        Vec3 origin = Vec3.atBottomCenterOf(pos).add(rotate(vector(point,"origin_offset",new Vec3(0,0,bed ? -.5 : 0)),yaw));
        if(bed && point.has("preserve_bed_side") && point.get("preserve_bed_side").getAsBoolean()) {
            var side=Vec3.atLowerCornerOf(selected.subtract(pos)).yRot((float)Math.toRadians(yaw-180));
            origin=origin.add(rotate(new Vec3(side.x,0,0),yaw));
        }
        Vec3 approach = origin.add(rotate(vector(point,"approach_offset",bed ? new Vec3(-14/16.0,0,10/16.0) : new Vec3(0,0,-13/16.0)),yaw));
        float approachYaw=point.has("approach_yaw_offset")?point.get("approach_yaw_offset").getAsFloat():bed?-90:0;
        return new NpcSupportTarget(pos,origin,approach,yaw,yaw+approachYaw);
    }

    // Authored relative block offset, in world axes: exactly one adjacent sofa seat.
    private static BlockPos span(JsonObject point) {
        var v=vector(point,"seat_span",Vec3.ZERO);
        var p=BlockPos.containing(v);
        return v.x==p.getX() && v.y==0 && v.z==p.getZ() && Math.abs(p.getX())+Math.abs(p.getZ())==1 ? p : null;
    }

    public static net.minecraft.world.phys.AABB occupiedArea(NpcSupportTarget target,String pointId) {
        var area=new net.minecraft.world.phys.AABB(target.block());
        var point=point(pointId);
        if (point!=null && point.has("seat_span")) {
            var step=span(point);
            if(step!=null) area=area.minmax(new net.minecraft.world.phys.AABB(target.block().offset(step)));
        }
        return area.inflate(.7,1.5,.7);
    }

    private static Vec3 vector(JsonObject point,String name,Vec3 fallback) {
        if (!point.has(name)) return fallback;
        var v=point.getAsJsonArray(name);
        if (v.size()!=3) throw new IllegalArgumentException("Support offset requires three coordinates");
        var value=new Vec3(v.get(0).getAsDouble(),v.get(1).getAsDouble(),v.get(2).getAsDouble());
        if (!Double.isFinite(value.lengthSqr()) || value.lengthSqr()>16) throw new IllegalArgumentException("Invalid support offset");
        return value;
    }

    public static Vec3 rotate(Vec3 local, float yaw) {
        return local.yRot((float)Math.toRadians(180-yaw));
    }

    public static boolean occupied(ServerLevel level, BlockPos main) {
        return !level.getEntitiesOfClass(com.stardew.craft.entity.npc.StardewNpcEntity.class,
                new net.minecraft.world.phys.AABB(main).inflate(3), npc -> {
                    var event = npc.getScheduleActivityEvent();
                    if (!event.contains("block")) return false;
                    var block=BlockPos.of(event.getLong("block"));
                    if(block.equals(main))return true;
                    var point=point(event.getString("point"));
                    var step=point!=null && point.has("seat_span") ? span(point) : null;
                    return step!=null && block.offset(step).equals(main);
                }).isEmpty();
    }

    public static Vec3 routePosition(ServerLevel level, String id, Vec3 fallback) {
        var support = resolve(level,id);
        // Missing furniture has no validated approach. The executor waits with a loading ticket.
        var point = point(id);
        return support != null ? support.approach() : point != null && point.has("furniture")
                ? null : fallback;
    }

    public static Vec3 pendingFurniturePosition(ServerLevel level,String id) {
        var point=point(id);
        if(point==null || !point.has("furniture") || resolve(level,id)!=null) return null;
        return NpcRoutePlanner.pointFromConfig(level,id,null);
    }
}
