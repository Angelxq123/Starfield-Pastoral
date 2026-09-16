package com.stardew.craft.client.monsternative;
import com.stardew.craft.client.npcnative.*;
/** Source fires immediately; recoil and expression changes add no gameplay delay. */
public final class NativeSquidKidPlayback {
    private final NativeNpcModel model;private final NativeNpcPose pose,capture;private boolean dying;private double previous=Double.NaN;
    public NativeSquidKidPlayback(NativeNpcModel m){model=m;pose=new NativeNpcPose(m);capture=new NativeNpcPose(m);}
    private void addHit(double t){float[] v=new float[3];for(var track:model.clips().get("animation.squid_kid.hit").tracks()){NativeNpcPose.sample(track,t,v);var bone=model.bones().get(track.bone()).name();if(track.channel().equals("rotation"))pose.addRotation(bone,v[0],v[1],v[2]);else if(track.channel().equals("position"))pose.addPosition(bone,v[0],v[1],v[2]);}}
    public NativeNpcPose pose(){return pose;}
    public void sample(double clock,double fire,double hit,double death){
        if(clock==previous)return;
        if(death>0){if(!dying)capture.copyFrom(pose);pose.reset();pose.apply("animation.squid_kid.death",death);pose.blendFrom(capture,1-NativeGrubMotion.smooth(death/.06));}
        else{pose.reset();pose.apply("animation.squid_kid.hover",clock);if(fire>=0&&fire<.16)pose.blend("animation.squid_kid.fire",fire,1-NativeGrubMotion.smooth(fire/.16));if(hit>=0&&hit<.2)addHit(hit);}
        dying=death>0;previous=clock;
    }
}
