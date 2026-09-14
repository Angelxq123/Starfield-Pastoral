package com.stardew.craft.client.monsternative;

import com.stardew.craft.client.npcnative.NativeNpcModel;
import com.stardew.craft.client.npcnative.NativeNpcPose;

/** Source cursor owns the drill cycle; visual reactions never extend the damage window. */
public final class NativeDuggyPlayback {
    private final NativeNpcModel model;
    private final NativeNpcPose pose,deathCapture;
    private boolean dying;
    private double lastClock=Double.NaN;
    public NativeDuggyPlayback(NativeNpcModel model){this.model=model;pose=new NativeNpcPose(model);deathCapture=new NativeNpcPose(model);}
    public NativeNpcPose pose(){return pose;}
    public void sample(double clock,double cursor,double hit,double death){
        if(clock==lastClock)return;
        if(death>0){
            if(!dying||!Double.isFinite(lastClock)||clock<lastClock||clock-lastClock>.5){live(cursor,hit-death);deathCapture.copyFrom(pose);}
            pose.reset();pose.apply("animation.duggy.death",death);pose.blendFrom(deathCapture,1-NativeGrubMotion.smooth(death/.15));
        }else live(cursor,hit);
        dying=death>0;lastClock=clock;
    }
    private void live(double cursor,double hit){
        pose.reset();String clip;double t;
        if(cursor<2){clip="warning";t=cursor*.1;}
        else if(cursor<4){clip="emerge";t=(cursor-2)*.1;}
        else if(cursor<8){clip="attack";t=(cursor-4)*.22;}
        else{clip="retreat";t=(cursor-8)*.22;}
        pose.apply("animation.duggy."+clip,t);
        if(hit>=0&&hit<.24)NativeMonsterMotion.addRotationClip(model,pose,"animation.duggy.hit",hit);
    }
}
