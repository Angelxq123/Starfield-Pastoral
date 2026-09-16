package com.stardew.craft.client.monsternative;
import com.stardew.craft.client.npcnative.*;
/** Source vertical offset owns deformation; hit and death start from the current living pose. */
public final class NativeDustSpiritPlayback {
    private final NativeNpcModel model;
    private final NativeNpcPose pose,deathCapture;
    private boolean dying;
    private double previousClock=Double.NaN,offset;
    public NativeDustSpiritPlayback(NativeNpcModel m){model=m;pose=new NativeNpcPose(m);deathCapture=new NativeNpcPose(m);}
    public NativeNpcPose pose(){return pose;}
    public void sample(double clock,double sourceOffset,double size,double hit,double death){
        if(clock==previousClock)return;
        if(!Double.isFinite(previousClock)||clock<previousClock||clock-previousClock>.5)offset=sourceOffset;
        else offset+=(sourceOffset-offset)*(1-Math.exp(-(clock-previousClock)*45));
        if(death>0){
            if(!dying){live(clock-death,offset,size,hit-death);deathCapture.copyFrom(pose);}
            pose.reset();pose.apply("animation.dust_spirit.death",death);pose.blendFrom(deathCapture,1-NativeGrubMotion.smooth(death/.12));
        }else live(clock,offset,size,hit);
        // Keep the signed hull above the ground even when a hit tilts a bottom corner down.
        float lowest=Float.POSITIVE_INFINITY;var matrices=pose.matrices();var v=new org.joml.Vector3f();
        for(var q:model.quads())if(q.sourcePart().endsWith("_outline"))for(var xyz:q.vertices()){
            matrices[q.bone()].transformPosition(v.set(xyz[0],xyz[1],xyz[2]));lowest=Math.min(lowest,v.y);
        }
        if(lowest<.3F)pose.addPosition("root",0,.3F-lowest,0);
        previousClock=clock;dying=death>0;
    }
    private void live(double clock,double sourceOffset,double size,double hit){
        pose.reset();pose.apply("animation.dust_spirit.hop",-sourceOffset/64);
        // Source adds squash to individual base scale, rather than multiplying both together.
        double sx=(size+Math.max(-.1,(sourceOffset+32)/128))/size,sy=(size-Math.max(-.1,sourceOffset/256))/size;
        pose.setScale("root",sx,sy,sx);
        NativeMonsterMotion.addRotationClip(model,pose,"animation.dust_spirit.flutter",clock%.62);
        if(hit>=0&&hit<.26)NativeMonsterMotion.addRotationClip(model,pose,"animation.dust_spirit.hit",hit);
    }
}
