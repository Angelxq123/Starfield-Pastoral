package com.stardew.craft.client.npcnative;

import com.stardew.craft.npc.attention.NpcAttentionMotion;

/** Whole-limb rig adapter with character-specific contact geometry. Apply after idle, before blink and matrix evaluation. */
public final class NativeSamAttentionPose {
    private NativeSamAttentionPose() {}

    public static void apply(NativeNpcPose pose, NpcAttentionMotion.Sample s, double weight) {
        apply(pose,s,weight,NpcAttentionMotion.SAM);
    }

    public static void apply(NativeNpcPose pose, NpcAttentionMotion.Sample s, double weight, NpcAttentionMotion.Rig rig) {
        double body=s.bodyYaw()*weight;
        pose.addRotation("root",0,body,0);
        pose.addPosition("root",s.rootX()*weight,0,s.rootZ()*weight);
        var limits=pose.lookLimits();
        double headYaw=limits==null?s.headYaw():softLimit(s.headYaw(),limits.yaw());
        double headPitch=limits==null?s.headPitch():softLimit(s.headPitch(),limits.pitch());
        // Shoulder-supported hair does not sweep through the chest with an unrestricted neck.
        // The torso carries the remaining aim, keeping the target and planted-foot turn intact.
        pose.addRotation("body",(s.headPitch()-headPitch)*weight,
                (s.shoulderYaw()+s.headYaw()-headYaw)*weight,s.lean()*weight);
        pose.addRotation("head",headPitch*weight,headYaw*weight,0);
        pose.addRotation("arm_right",s.armSwing()*weight,0,0);
        pose.addRotation("arm_left",-s.armSwing()*weight,0,0);
        if(pose.hasBone("cloth_motion"))
            pose.addPosition("cloth_motion",-s.shoulderYaw()*.025*weight,0,Math.abs(s.lean())*.025*weight);
        // Iris/eye transforms deliberately stay untouched, including during attention reactions.
        foot(pose,"leg_right",s.right(),body,s.rootX()*weight,s.rootZ()*weight,weight,true,rig);
        foot(pose,"leg_left",s.left(),body,s.rootX()*weight,s.rootZ()*weight,weight,false,rig);
    }

    private static double softLimit(double angle,double limit) {
        return limit*Math.tanh(angle/limit);
    }

    private static void foot(NativeNpcPose pose,String bone,NpcAttentionMotion.Foot foot,
                             double body,double rootX,double rootZ,double weight,boolean right,NpcAttentionMotion.Rig rig) {
        double x=rig.toeX(right)+(foot.toeX()-rig.toeX(right))*weight-rootX;
        double z=rig.toeZ(right)+(foot.toeZ()-rig.toeZ(right))*weight-rootZ;
        double root=Math.toRadians(body), localYaw=Math.toRadians(rig.restYaw(right)+foot.angle()*weight-body);
        double heel=Math.toRadians(foot.heel()*weight);
        // Solve the hip translation from the toe contact after the whole leg rotates around its hip.
        double length=rig.hipHeight()-rig.soleY();
        double toeY=-length*Math.cos(heel)+rig.toeDepth()*Math.sin(heel);
        double toeZ=-length*Math.sin(heel)-rig.toeDepth()*Math.cos(heel);
        double localX=Math.cos(root)*x-Math.sin(root)*z;
        double localZ=Math.sin(root)*x+Math.cos(root)*z;
        double restX=rig.restX(right), restZ=rig.restZ(right);
        pose.addPosition(bone,localX-Math.sin(localYaw)*toeZ-restX,
                foot.lift()*weight+rig.soleY()-rig.hipHeight()-toeY,localZ-Math.cos(localYaw)*toeZ-restZ);
        pose.addRotation(bone,foot.heel()*weight,foot.angle()*weight-body,0);
    }
}
