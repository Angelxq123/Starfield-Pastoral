package com.stardew.craft.fishpond.service;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import java.util.*;
import java.util.function.Predicate;

/** Water-volume navigation. Checks the whole fish envelope, not a 2D pond rectangle. */
public final class PondSwimSpace {
    public static final double RADIUS=.36;
    private final Set<Long> water;
    private final Predicate<BlockPos> wet;
    public PondSwimSpace(Set<Long> water,Predicate<BlockPos> wet) {this.water=Set.copyOf(water);this.wet=wet;}
    private boolean contains(BlockPos p) {return water.contains(p.asLong())&&wet.test(p);}
    public boolean fits(Vec3 p) {
        var low=BlockPos.containing(p.x-RADIUS,p.y-RADIUS,p.z-RADIUS);
        var high=BlockPos.containing(p.x+RADIUS,p.y+RADIUS,p.z+RADIUS);
        for(var cell:BlockPos.betweenClosed(low,high)) {
            if(!contains(cell))return false;
            if(!contains(cell.above()) && p.y+RADIUS>cell.getY()+8./9)return false;
        }
        return true;
    }
    public boolean clear(Vec3 from,Vec3 to) {
        int steps=Math.max(1,(int)Math.ceil(from.distanceTo(to)/.15));
        for(int i=0;i<=steps;i++)if(!fits(from.lerp(to,(double)i/steps)))return false;
        return true;
    }
    public List<Vec3> route(Vec3 from,Vec3 to) {
        if(clear(from,to))return List.of(to);
        BlockPos start=BlockPos.containing(from),end=BlockPos.containing(to);
        var queue=new ArrayDeque<BlockPos>();var previous=new HashMap<BlockPos,BlockPos>();
        if(!clear(from,Vec3.atCenterOf(start)))return List.of();
        queue.add(start);previous.put(start,start);
        while(!queue.isEmpty()&&previous.size()<=2048) {
            var current=queue.remove();
            if(current.equals(end)) {
                var path=new ArrayList<Vec3>();path.add(to);
                for(var p=end;!p.equals(start);p=previous.get(p))path.add(Vec3.atCenterOf(p));
                path.add(Vec3.atCenterOf(start));Collections.reverse(path);return path;
            }
            for(var direction:Direction.values()) {
                var next=current.relative(direction);
                if(!previous.containsKey(next)&&contains(next)&&clear(Vec3.atCenterOf(current),Vec3.atCenterOf(next))) {
                    previous.put(next,current);queue.add(next);
                }
            }
        }
        return List.of();
    }
    public Vec3 sample(RandomSource random,boolean bottom) {
        var candidates=new ArrayList<Long>(water);Collections.sort(candidates);
        if(candidates.isEmpty())return null;
        for(int i=0;i<80;i++) {
            var cell=BlockPos.of(candidates.get(random.nextInt(candidates.size())));
            if(bottom && contains(cell.below()))continue;
            var p=Vec3.atCenterOf(cell).add((random.nextDouble()-.5)*.15,bottom?-.08:(random.nextDouble()-.5)*.12,(random.nextDouble()-.5)*.15);
            if(fits(p))return p;
        }
        return null;
    }
}
