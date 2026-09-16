package com.stardew.craft.client.weapon.animation;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Vector3f;
import static com.stardew.craft.combat.skill.handler.HeavyHammerRules.*;
import static com.stardew.craft.client.weapon.animation.WeaponSkillKeyframeTimeline.Easing.*;

/** Broad shoulder-driven skill strokes; normal attacks retain their existing timing. */
public final class HeavyHammerAnimation implements WeaponSkillAnimation {
    public static final String GALAXY_SWING="galaxy_hammer_swing", INFINITY_SWING="infinity_gavel_swing";
    private static final WeaponSkillPose REST=p(0,-90,25,1.13f,3.2f,1.13f);
    private static final Vector3f GRIP=new Vector3f(0,-.375f,0);
    private static final WeaponSkillPose SWEEP_BACK=p(-18,-58,-40,-2.70f,10.71f,-3.91f), SWEEP_HIT=p(5,-30,90,-11.52f,3.65f,-3.36f);
    private static final WeaponSkillPose HIGH=p(-72,-65,-12,-3.69f,10.75f,-1.14f), GROUND=p(34,-40,62,-8.66f,0.24f,-6.17f);
    private static final WeaponSkillPose PRESS_BACK=p(-52,-62,-24,-3.32f,10.10f,-2.70f), PRESS_HIT=p(24,-38,60,-8.72f,1.70f,-5.96f);
    private static final WeaponSkillKeyframeTimeline SWEEP_POSE=new WeaponSkillKeyframeTimeline(12,REST,GRIP,
            key(0,REST,LINEAR),key(3,SWEEP_BACK,SMOOTH),key(3.5f,SWEEP_BACK,LINEAR),
            key(5,SWEEP_HIT,EASE_IN_QUAD),key(5.8f,SWEEP_HIT,LINEAR),
            key(7,p(8,-42,105,-13.46f,3.43f,-1.80f),EASE_OUT_CUBIC),key(12,REST,SMOOTH));
    private static final WeaponSkillKeyframeTimeline QUAKE_POSE=new WeaponSkillKeyframeTimeline(16,REST,GRIP,
            key(0,REST,LINEAR),key(4.8f,HIGH,SMOOTH),key(5.4f,HIGH,LINEAR),
            key(7,GROUND,EASE_IN_QUAD),key(8.4f,GROUND,LINEAR),
            key(11,p(8,-60,50,-5.80f,1.57f,-2.90f),EASE_OUT_CUBIC),key(16,REST,SMOOTH));
    private static final WeaponSkillKeyframeTimeline PRESS_POSE=new WeaponSkillKeyframeTimeline(15,REST,GRIP,
            key(0,REST,LINEAR),key(5.5f,PRESS_BACK,SMOOTH),key(6.2f,PRESS_BACK,LINEAR),
            key(8,PRESS_HIT,EASE_IN_QUAD),key(9,PRESS_HIT,LINEAR),
            key(11,p(-18,-62,8,-5.26f,6.90f,-2.80f),EASE_OUT_CUBIC),
            key(13,p(20,-38,52,-8.69f,2.20f,-5.70f),EASE_IN_QUAD),key(13.5f,p(20,-38,52,-8.69f,2.20f,-5.70f),LINEAR),key(15,REST,SMOOTH));
    private static final WeaponSkillKeyframeTimeline POUND_POSE=new WeaponSkillKeyframeTimeline(7,REST,GRIP,
            key(0,REST,LINEAR),key(1,p(-60,-64,-12,-3.84f,10.58f,-1.98f),EASE_OUT_CUBIC),
            key(2,GROUND,EASE_IN_QUAD),key(2.7f,GROUND,LINEAR),
            key(4.8f,p(4,-60,45,-5.41f,1.81f,-2.75f),EASE_OUT_CUBIC),key(7,REST,SMOOTH));
    private static final WeaponSkillKeyframeTimeline NORMAL=new WeaponSkillKeyframeTimeline(7,REST,GRIP,
            key(0,REST,LINEAR),key(.845f,p(-35,-80,10,1.8f,4.8f,.2f),EASE_OUT_CUBIC),
            key(1.3f,p(-8,-76,38,.25f,.7f,-3.7f),EASE_IN_QUAD),key(2.3f,p(-8,-76,38,.25f,.7f,-3.7f),LINEAR),key(7,REST,SMOOTH));
    private static final WeaponSkillKeyframeTimeline READY=new WeaponSkillKeyframeTimeline(3,REST,GRIP,
            key(0,REST,LINEAR),key(1.5f,p(-28,-90,-5,2.2f,4.4f,.5f),EASE_OUT_CUBIC),key(3,REST,SMOOTH));
    private final String skill;
    public HeavyHammerAnimation(String skill){this.skill=skill;}
    public static boolean supports(String id){return SWEEP.equals(id)||QUAKE.equals(id)||PRESS.equals(id)||ENDLESS.equals(id)||POUND.equals(id)||GALAXY_SWING.equals(id)||INFINITY_SWING.equals(id);}
    private static WeaponSkillKeyframeTimeline timeline(String id){return SWEEP.equals(id)?SWEEP_POSE:QUAKE.equals(id)?QUAKE_POSE:PRESS.equals(id)?PRESS_POSE:ENDLESS.equals(id)?READY:POUND.equals(id)?POUND_POSE:NORMAL;}
    static WeaponSkillPose sample(String id,float t){return timeline(id).sampleRight(t);}
    @Override public boolean apply(PoseStack pose,HumanoidArm arm,float t){return timeline(skill).apply(pose,arm,t);}

    // Body sampling follows the same continuous pose as the held item, including contact holds.
    static BodyPose body(String id,float t){
        return body(sample(id,t),SWEEP.equals(id),t);
    }
    static BodyPose body(WeaponSkillPose pose,boolean sweep,float t){
        float envelope=(float)Math.sin(Math.PI*Mth.clamp(t,0,1));
        float lift=Mth.clamp((pose.ty()-REST.ty())/7,0,1),drop=Mth.clamp((REST.ty()-pose.ty())/3,0,1);
        float sweepTurn=Mth.clamp((REST.rz()-pose.rz())/90,-1,1);
        return new BodyPose(sweep?-envelope*1.25f-lift*.45f:-envelope*.5f-lift*2-drop*.35f,
                sweep?sweepTurn*.9f:envelope*-.08f,
                sweep?sweepTurn*.2f:envelope*.08f,
                -lift*.16f+drop*.32f,
                sweep?sweepTurn*.4f:envelope*-.1f,
                lift,drop);
    }
    record BodyPose(float armX,float armY,float armZ,float bodyX,float bodyY,float lift,float drop) {}
    static void applyBodyPose(HumanoidModel<?> model,LivingEntity actor,String id,float t){
        applyBodyFromPose(model,actor,sample(id,t),t,SWEEP.equals(id));
    }
    static void applyBodyFromPose(HumanoidModel<?> model,LivingEntity actor,WeaponSkillPose pose,float t,boolean sweep){
        BodyPose p=body(pose,sweep,t);float sign=actor.getMainArm()==HumanoidArm.RIGHT?1:-1;
        var arm=sign>0?model.rightArm:model.leftArm;
        arm.xRot+=p.armX;arm.yRot+=sign*p.armY;arm.zRot+=sign*p.armZ;
        model.body.xRot+=p.bodyX;model.body.yRot+=sign*p.bodyY;
        model.rightLeg.xRot-=p.drop*.16f;model.leftLeg.xRot+=p.drop*.16f;
    }
    private static WeaponSkillPose p(float x,float y,float z,float tx,float ty,float tz){return new WeaponSkillPose(x,y,z,tx,ty,tz);}
    private static WeaponSkillKeyframeTimeline.Keyframe key(float t,WeaponSkillPose p,WeaponSkillKeyframeTimeline.Easing e){return new WeaponSkillKeyframeTimeline.Keyframe(t,p,e);}
}
