package com.stardew.craft.client.fishing;

import org.joml.Matrix3f;
import org.joml.Vector3f;

/** Six articulated skin intervals, matching the approved authoring solver's palm path. */
public final class FishingArmIK {
    private record Solution(Matrix3f upper,float bend,Matrix3f hand) {}
    private static Vector3f palm(float angle) {
        float y=-3.5f,z=0;for(int i=1;i<6;i++){y-=(float)Math.cos(angle*i/6);z-=(float)Math.sin(angle*i/6);}
        return new Vector3f(0,y-1.5f*(float)Math.cos(angle),z-1.5f*(float)Math.sin(angle));
    }
    private static Solution solve(Vector3f shoulder,Vector3f target,Vector3f pole) {
        var d=new Vector3f(target).sub(shoulder);float length=d.length();d.normalize();float lo=0,hi=(float)Math.toRadians(155);
        for(int i=0;i<22;i++){float middle=(lo+hi)*.5f;if(palm(middle).length()>length)lo=middle;else hi=middle;}
        float bend=(lo+hi)*.5f;var p=palm(bend);
        var x=new Vector3f(pole).cross(d).normalize();var y=new Vector3f(d).negate();var z=new Vector3f(x).cross(y);
        var world=new Matrix3f().setColumn(0,x).setColumn(1,y).setColumn(2,z);
        var localY=new Vector3f(p).normalize().negate();var local=new Matrix3f().setColumn(0,new Vector3f(1,0,0)).setColumn(1,localY).setColumn(2,new Vector3f(1,0,0).cross(localY));
        var upper=world.mul(local.transpose());return new Solution(upper,bend,new Matrix3f(upper).rotateX(bend));
    }
    private static float separation(Vector3f a,Matrix3f ar,Vector3f ah,Vector3f b,Matrix3f br,Vector3f bh) {
        Vector3f[] axes=new Vector3f[15];for(int i=0;i<3;i++){axes[i]=ar.getColumn(i,new Vector3f());axes[i+3]=br.getColumn(i,new Vector3f());}
        for(int i=0;i<3;i++)for(int j=0;j<3;j++)axes[6+i*3+j]=new Vector3f(axes[i]).cross(axes[j+3]);
        float max=-Float.MAX_VALUE;var delta=new Vector3f(b).sub(a);
        for(var axis:axes){if(axis.lengthSquared()<1e-8)continue;axis.normalize();float ra=0,rb=0;for(int i=0;i<3;i++){ra+=Math.abs(axis.dot(ar.getColumn(i,new Vector3f())))*ah.get(i);rb+=Math.abs(axis.dot(br.getColumn(i,new Vector3f())))*bh.get(i);}max=Math.max(max,Math.abs(axis.dot(delta))-ra-rb);}return max;
    }
    public static void grip(FishingRigPose pose,boolean right,float weight) {
        if(weight<=0)return;String side=right?"right":"left";
        // Early cube-arm clips lifted their skin by one pixel. The continuous chain starts
        // at the shoulder socket; retaining that lift moves the solved palm off the grip.
        if(right){pose.channels[pose.index("arm_skin_right")][1]=0;pose.matrices();}
        int arm=pose.index("arm_"+side);var shoulder=pose.anchor("arm_"+side);
        var rod=new Matrix3f(pose.world[pose.index("rod_rod")]);
        Vector3f center=right?pose.world[pose.index("rod_rod")].transformPosition(new Vector3f(0,.3f,0)):pose.world[pose.index("rod_crank")].transformPosition(new Vector3f(.5f,1.875f,-.5f));
        var box=right?rod:new Matrix3f(pose.world[pose.index("rod_crank")]);
        var half=right?new Vector3f(1.5f,.2f,1.5f):new Vector3f(.5f,.375f,.5f);
        var normal=right?new Vector3f(1,0,0):rod.transform(new Vector3f(1,0,0));
        var currentHand=pose.world[pose.index(side+"_elbow_skin_6")].transformPosition(new Vector3f(0,-1.5f,0));
        var direction=new Vector3f(currentHand).sub(shoulder).normalize();var x=pose.world[arm].transformDirection(new Vector3f(1,0,0)).normalize();var pole=new Vector3f(direction).cross(x).normalize();
        float lo=0,hi=6;for(int i=0;i<18;i++){float d=(lo+hi)*.5f;var target=new Vector3f(center).fma(d,normal);var result=solve(shoulder,target,pole);if(separation(result.upper.transform(palm(result.bend)).add(shoulder),result.hand,new Vector3f(2),center,box,half)>-.08f)hi=d;else lo=d;}
        var solved=solve(shoulder,new Vector3f(center).fma((lo+hi)*.5f,normal),pole);
        int parent=pose.rig.bones().get(arm).parent();var relative=new Matrix3f(pose.world[parent]).invert().mul(solved.upper);var angles=relative.getEulerAnglesZYX(new Vector3f()).mul(180/(float)Math.PI);
        for(int axis=0;axis<3;axis++){float old=pose.channels[arm][3+axis],delta=(angles.get(axis)-old)%360;delta=(delta+540)%360-180;pose.channels[arm][3+axis]=old+delta*weight;}
        for(int i=1;i<=6;i++){int bone=pose.index(side+"_elbow_skin_"+i);pose.channels[bone][3]+=(solved.bend*180/(float)Math.PI/6-pose.channels[bone][3])*weight;}
        pose.matrices();
    }
}
