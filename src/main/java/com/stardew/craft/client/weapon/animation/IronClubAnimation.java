package com.stardew.craft.client.weapon.animation;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Vector3f;
import static com.stardew.craft.combat.skill.handler.IronClubRules.*;
import static com.stardew.craft.client.weapon.animation.WeaponSkillKeyframeTimeline.Easing.*;

public final class IronClubAnimation implements WeaponSkillAnimation {
    private static final WeaponSkillPose REST=new WeaponSkillPose(0,-90,25,1.13f,3.2f,1.13f);
    private static final Vector3f GRIP=new Vector3f(0,-.375f,0);
    private static final WeaponSkillPose LEAD_WIND=new WeaponSkillPose(-62,-62,-22,-3.41f,10.89f,-2.50f);
    private static final WeaponSkillPose LEAD_HIT=new WeaponSkillPose(24,-38,68,-10.32f,0.46f,-5.23f);
    private static final WeaponSkillPose LONG_WIND=new WeaponSkillPose(-14,-55,-46,-1.95f,11.21f,-4.13f);
    private static final WeaponSkillPose LONG_HIT=new WeaponSkillPose(12,-30,98,-13.73f,3.39f,-3.42f);
    private static final WeaponSkillKeyframeTimeline PRESS_POSE=new WeaponSkillKeyframeTimeline(20,REST,GRIP,
            k(0,REST,LINEAR),k(5.5f,LEAD_WIND,SMOOTH),k(7,LEAD_WIND,LINEAR),k(9,LEAD_HIT,EASE_IN_QUAD),k(11,LEAD_HIT,LINEAR),k(20,REST,SMOOTH));
    private static final WeaponSkillKeyframeTimeline SWEEP_POSE=new WeaponSkillKeyframeTimeline(22,REST,GRIP,
            k(0,REST,LINEAR),k(6,LONG_WIND,SMOOTH),k(8.5f,LONG_WIND,LINEAR),k(11,LONG_HIT,EASE_IN_QUAD),k(13,LONG_HIT,LINEAR),k(22,REST,SMOOTH));
    private static final WeaponSkillKeyframeTimeline LEAD_NORMAL=normal(new WeaponSkillPose(-28,-83,12,1.9f,4.6f,.6f),new WeaponSkillPose(-9,-82,41,-.5f,1.1f,-3.4f)),LONG_NORMAL=normal(new WeaponSkillPose(-19,-67,8,3.1f,3.9f,.35f),new WeaponSkillPose(-17,-83,51,-2.6f,1.9f,-3.2f));
    private final String skill;
    public IronClubAnimation(String skill){this.skill=skill;}
    public static boolean supports(String id){return PRESS.equals(id)||SWEEP.equals(id)||LEAD_SWING.equals(id)||KUDGEL_SWING.equals(id);}
    private static WeaponSkillKeyframeTimeline timeline(String id){return PRESS.equals(id)?PRESS_POSE:SWEEP.equals(id)?SWEEP_POSE:LEAD_SWING.equals(id)?LEAD_NORMAL:LONG_NORMAL;}
    static WeaponSkillPose sample(String id,float t){return timeline(id).sampleRight(t);}
    @Override public boolean apply(PoseStack stack,HumanoidArm arm,float t){return timeline(skill).apply(stack,arm,t);}
    static void applyBodyPose(HumanoidModel<?> model,LivingEntity actor,String id,float t) {
        HeavyHammerAnimation.applyBodyFromPose(model,actor,sample(id,t),t,SWEEP.equals(id)||KUDGEL_SWING.equals(id));
    }
    private static WeaponSkillKeyframeTimeline normal(WeaponSkillPose wind,WeaponSkillPose hit){return new WeaponSkillKeyframeTimeline(7,REST,GRIP,k(0,REST,LINEAR),k(.6f,wind,EASE_OUT_CUBIC),k(1.3f,hit,EASE_IN_QUAD),k(2,hit,LINEAR),k(7,REST,SMOOTH));}
    private static WeaponSkillKeyframeTimeline.Keyframe k(float t,WeaponSkillPose p,WeaponSkillKeyframeTimeline.Easing e){return new WeaponSkillKeyframeTimeline.Keyframe(t,p,e);}
}
