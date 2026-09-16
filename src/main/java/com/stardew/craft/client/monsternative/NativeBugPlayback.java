package com.stardew.craft.client.monsternative;

import com.stardew.craft.client.npcnative.NativeNpcModel;
import com.stardew.craft.client.npcnative.NativeNpcPose;

/** Separate world-axis sway from model yaw, so a collision reversal never flips the sway. */
public final class NativeBugPlayback {
    private final NativeNpcModel model;
    private final NativeNpcPose pose,captured;
    private double lastClock=Double.NaN,swayX,swayZ;
    private boolean dying;
    public NativeBugPlayback(NativeNpcModel model) { this.model=model;pose=new NativeNpcPose(model);captured=new NativeNpcPose(model); }
    public NativeNpcPose pose() { return pose; }
    public double swayX() { return swayX; }
    public double swayZ() { return swayZ; }
    public void sample(double clock,double hitAge,double deathTime,int facing) {
        if(clock==lastClock)return;
        boolean reset=!Double.isFinite(lastClock)||clock<lastClock||clock-lastClock>.5;
        if(deathTime>0) {
            if(!dying||reset) {
                if(reset) {
                    NativeBugMotion.flight(pose,clock-deathTime,0);
                    double hit=hitAge-deathTime;
                    if(hit>=0&&hit<.24)NativeBugMotion.hit(pose,hit);
                }
                captured.copyFrom(pose);
                sway(clock-deathTime,facing);
            }
            NativeBugMotion.death(model,pose,captured,deathTime);
        } else {
            NativeBugMotion.flight(pose,clock,0);
            if(hitAge>=0&&hitAge<.24)NativeBugMotion.hit(pose,hitAge);
            sway(clock,facing);
        }
        dying=deathTime>0;lastClock=clock;
    }
    private void sway(double clock,int facing) {
        double offset=Math.sin(clock*2*Math.PI)*10/64;
        swayX=facing%2==0?offset:0;swayZ=facing%2!=0?offset:0;
    }
}
