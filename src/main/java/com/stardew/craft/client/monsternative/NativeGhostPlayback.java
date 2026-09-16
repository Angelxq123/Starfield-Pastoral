package com.stardew.craft.client.monsternative;
import com.stardew.craft.client.npcnative.*;
/** Moving cloth owns local pose; the entity owns hovering height and instantaneous relocation. */
public final class NativeGhostPlayback {
    private final NativeNpcModel model;private final NativeNpcPose pose,capture;private boolean dying;private double previous=Double.NaN;
    public NativeGhostPlayback(NativeNpcModel m){model=m;pose=new NativeNpcPose(m);capture=new NativeNpcPose(m);}
    public NativeNpcPose pose(){return pose;}
    public void sample(double clock,boolean slowed,double hit,double death){
        if(clock==previous)return;
        if(death>0){if(!dying){live(clock-death,slowed,hit-death);capture.copyFrom(pose);}pose.reset();pose.apply("animation.ghost.death",death);pose.blendFrom(capture,1-NativeGrubMotion.smooth(death/.1));}
        else live(clock,slowed,hit);
        previous=clock;dying=death>0;
    }
    private void live(double clock,boolean slowed,double hit){pose.reset();pose.apply("animation.ghost.hover",clock);if(hit>=0&&hit<.32)NativeMonsterMotion.addRotationClip(model,pose,"animation.ghost.hit",hit);}
}
