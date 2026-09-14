package com.stardew.craft.client.monsternative;

import com.stardew.craft.client.npcnative.NativeNpcModel;
import com.stardew.craft.client.npcnative.NativeNpcPose;
import org.joml.Vector3f;

public final class NativeFlyMotion {
    private NativeFlyMotion() {}
    public static void flight(NativeNpcPose pose,double clock,double spawnAge,double hitAge) {
        pose.reset();pose.apply("animation.fly.fly",clock);pose.apply("animation.fly.spawn",spawnAge);
        if(hitAge>=0&&hitAge<.32)pose.apply("animation.fly.hit",hitAge);
    }
    public static void death(NativeNpcModel model,NativeNpcPose pose,NativeNpcPose captured,double time) {
        pose.reset();pose.apply("animation.fly.death",time);
        pose.blendFrom(captured,1-NativeGrubMotion.smooth(time/.15));
        float low=Float.POSITIVE_INFINITY;var v=new Vector3f();var matrices=pose.matrices();
        for(var q:model.quads())for(var p:q.vertices())low=Math.min(low,matrices[q.bone()].transformPosition(v.set(p[0],p[1],p[2])).y);
        if(low<.25F)pose.addPosition("root",0,.25F-low,0);
    }
}
