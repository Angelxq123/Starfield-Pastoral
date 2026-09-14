package com.stardew.craft.client.monsternative;
import com.stardew.craft.client.npcnative.*;
/** Continuous 360ms nine-frame swim; hit is an additive recoil, never a reset of the tail wave. */
public final class NativeSerpentPlayback {
 private final NativeNpcModel model;private final NativeNpcPose pose,capture;private boolean dying;private double previous=Double.NaN;
 public NativeSerpentPlayback(NativeNpcModel m){model=m;pose=new NativeNpcPose(m);capture=new NativeNpcPose(m);}public NativeNpcPose pose(){return pose;}
 public void sample(double clock,double hit,double death){sample(clock,hit,death,3);}
 public void sample(double clock,double hit,double death,int spin){if(clock==previous)return;if(death>0){if(!dying){live(clock-death,hit-death);capture.copyFrom(pose);}pose.reset();pose.apply("animation.serpent.death",death);pose.blendFrom(capture,1-NativeGrubMotion.smooth(death/.12));}else live(clock,hit);if(death>0)pose.addRotation("root",0,0,(spin-3)*168.75*death*NativeGrubMotion.smooth(death/.12));previous=clock;dying=death>0;}
 private void live(double clock,double hit){pose.reset();pose.apply("animation.serpent.fly",clock);if(hit>=0&&hit<.25)NativeMonsterMotion.addRotationClip(model,pose,"animation.serpent.hit",hit);}
}
