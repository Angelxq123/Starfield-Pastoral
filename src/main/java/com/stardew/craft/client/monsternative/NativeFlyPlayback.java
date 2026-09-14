package com.stardew.craft.client.monsternative;

import com.stardew.craft.client.npcnative.NativeNpcModel;
import com.stardew.craft.client.npcnative.NativeNpcPose;

public final class NativeFlyPlayback {
    private final NativeNpcModel model;
    private final NativeNpcPose pose,captured;
    private double lastClock=Double.NaN;
    private boolean dying;
    public NativeFlyPlayback(NativeNpcModel model) { this.model=model;pose=new NativeNpcPose(model);captured=new NativeNpcPose(model); }
    public NativeNpcPose pose() { return pose; }
    public void sample(double clock,double spawnAge,double hitAge,double deathTime) {
        if(clock==lastClock)return;
        boolean reset=!Double.isFinite(lastClock)||clock<lastClock||clock-lastClock>.5;
        if(deathTime>0) {
            if(!dying||reset) {
                if(reset)NativeFlyMotion.flight(pose,clock-deathTime,Math.max(0,spawnAge-deathTime),hitAge-deathTime);
                captured.copyFrom(pose);
            }
            NativeFlyMotion.death(model,pose,captured,deathTime);
        } else NativeFlyMotion.flight(pose,clock,spawnAge,hitAge);
        lastClock=clock;dying=deathTime>0;
    }
}
