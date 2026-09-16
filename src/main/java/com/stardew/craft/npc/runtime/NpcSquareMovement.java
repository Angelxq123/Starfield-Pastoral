package com.stardew.craft.npc.runtime;

import com.stardew.craft.entity.npc.StardewNpcEntity;
import com.stardew.craft.interior.InteriorRegionRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/** Per-arrival square behavior. Navigation remains owned by the central schedule executor. */
public final class NpcSquareMovement {
    private static final Pattern TOKEN=Pattern.compile("square_([1-9][0-9]*)_([1-9][0-9]*)(?:_([0-3]))?");
    public record Behavior(int width,int height,int facing) {}
    public static Behavior parse(String token) {
        if(token==null)return null;var match=TOKEN.matcher(token);
        if(!match.matches())return null;
        try{return new Behavior(Integer.parseInt(match.group(1)),Integer.parseInt(match.group(2)),match.group(3)==null?-1:Integer.parseInt(match.group(3)));}
        catch(NumberFormatException invalid){return null;}
    }
    public final NpcSquareArea area;
    public final Behavior behavior;
    private final String region;
    private final Map<BlockPos,Long> rejected=new HashMap<>();
    private long lastTick=-1,cycleEnd,legStart,retryAt;
    private Vec3 target;
    public NpcSquareMovement(NpcSquareArea area,Behavior behavior,BlockPos anchor) {
        this.area=area;this.behavior=behavior;region=InteriorRegionRegistry.fixedInteriorIdAt(anchor);
    }
    public Vec3 target(){return target;}
    /** Freeze timing while dialogue, authored control, unload or attention bypasses execution. */
    public void resume(long now) {
        if(lastTick>=0){long paused=Math.max(0,now-lastTick-1);cycleEnd+=paused;legStart+=paused;retryAt+=paused;}
        lastTick=now;
    }
    public boolean waiting(long now){return now<retryAt;}
    public boolean arrived(long now){return now>=cycleEnd;}
    public boolean timedOut(long now){return target!=null&&now-legStart>=200;}
    public void reject(long now) {
        if(target!=null)rejected.put(BlockPos.containing(target),now+240);
        target=null;retryAt=now+20;
    }
    public void next(){target=null;}
    public Vec3 choose(ServerLevel level,StardewNpcEntity npc,long now) {
        rejected.entrySet().removeIf(e->e.getValue()<=now);
        var random=npc.getRandom();
        // Bounded sampling, one path search per selected target through the shared budget.
        for(int attempt=0;attempt<16;attempt++) {
            var cell=area.min().offset(random.nextInt(area.max().getX()-area.min().getX()+1),
                    random.nextInt(area.max().getY()-area.min().getY()+1),random.nextInt(area.max().getZ()-area.min().getZ()+1));
            var candidate=Vec3.atBottomCenterOf(cell);
            if(rejected.containsKey(cell)||!level.hasChunkAt(cell)||!region.equals(InteriorRegionRegistry.fixedInteriorIdAt(cell))
                    ||!area.contains(candidate,npc.getBbWidth()/2.)
                    ||!level.getFluidState(cell).isEmpty())continue;
            var box=npc.getBoundingBox().move(candidate.subtract(npc.position()));
            if(!level.noCollision(npc,box.deflate(1e-6))||level.noCollision(npc,box.move(0,-.05,0).deflate(1e-6)))continue;
            target=candidate;legStart=now;
            // Original route passes a 6000 ms offset to random's 6000..12000 ms range.
            cycleEnd=now+240+random.nextInt(120);return target;
        }
        retryAt=now+40;return null;
    }
}
