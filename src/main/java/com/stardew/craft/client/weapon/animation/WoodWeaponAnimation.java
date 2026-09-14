package com.stardew.craft.client.weapon.animation;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Vector3f;
import static com.stardew.craft.combat.skill.handler.WoodWeaponRules.*;
import static com.stardew.craft.client.weapon.animation.WeaponSkillKeyframeTimeline.Easing.*;

public final class WoodWeaponAnimation implements WeaponSkillAnimation {
    private static final WeaponSkillPose REST=new WeaponSkillPose(0,-90,25,1.13f,3.2f,1.13f);
    private static final Vector3f GRIP=new Vector3f(0,-.375f,0);
    private static final WeaponSkillPose WIND=new WeaponSkillPose(-20,-66,5,2.7f,3.9f,.4f),HIT=new WeaponSkillPose(-15,-84,48,-1.7f,2,-2.9f);
    private static final WeaponSkillPose WHIRL_BACK=new WeaponSkillPose(-20,-60,-38,-2.69f,10.24f,-4.20f),WHIRL_HIT=new WeaponSkillPose(8,-32,88,-11.92f,3.76f,-3.45f);
    private static final WeaponSkillPose WHIRL_RELOAD=new WeaponSkillPose(12,-50,98,-13.21f,7.70f,-1.09f),WHIRL_FINISH=new WeaponSkillPose(-8,-38,-36,-2.52f,4.99f,-5.93f);
    private static final WeaponSkillKeyframeTimeline WHIRL_POSE=new WeaponSkillKeyframeTimeline(18,REST,GRIP,
            k(0,REST,LINEAR),k(3,WHIRL_BACK,SMOOTH),k(3.6f,WHIRL_BACK,LINEAR),k(5,WHIRL_HIT,EASE_IN_QUAD),k(5.8f,WHIRL_HIT,LINEAR),
            k(9.5f,WHIRL_RELOAD,SMOOTH),k(12,WHIRL_FINISH,EASE_IN_QUAD),k(13,WHIRL_FINISH,LINEAR),k(18,REST,SMOOTH));
    private static final WeaponSkillPose LEAP_HIGH=new WeaponSkillPose(-64,-64,-14,-3.94f,10.92f,-1.73f),LEAP_DOWN=new WeaponSkillPose(28,-38,60,-8.71f,0.67f,-5.83f);
    private static final WeaponSkillKeyframeTimeline LEAP_POSE=new WeaponSkillKeyframeTimeline(14,REST,GRIP,
            k(0,REST,LINEAR),k(3.5f,LEAP_HIGH,SMOOTH),k(5.2f,LEAP_HIGH,LINEAR),
            k(7,LEAP_DOWN,EASE_IN_QUAD),k(8.2f,LEAP_DOWN,LINEAR),k(10.5f,new WeaponSkillPose(5,-60,45,-5.33f,1.53f,-3.12f),EASE_OUT_CUBIC),k(14,REST,SMOOTH));
    private static final WeaponSkillKeyframeTimeline CLUB_POSE=new WeaponSkillKeyframeTimeline(7,REST,GRIP,k(0,REST,LINEAR),k(.6f,WIND,EASE_OUT_CUBIC),k(1.3f,HIT,EASE_IN_QUAD),k(2,HIT,LINEAR),k(7,REST,SMOOTH));
    private static final WeaponSkillKeyframeTimeline MALLET_POSE=new WeaponSkillKeyframeTimeline(7,REST,GRIP,
            k(0,REST,LINEAR),k(.6f,new WeaponSkillPose(-32,-80,12,1.6f,4.5f,.2f),EASE_OUT_CUBIC),
            k(1.3f,new WeaponSkillPose(-8,-76,38,.25f,.7f,-3.7f),EASE_IN_QUAD),
            k(2,new WeaponSkillPose(-8,-76,38,.25f,.7f,-3.7f),LINEAR),k(7,REST,SMOOTH));
    private final String skill;
    public WoodWeaponAnimation(String skill){this.skill=skill;}
    public static boolean supports(String id){return WHIRL.equals(id)||LEAP.equals(id)||CLUB_SWING.equals(id)||MALLET_SWING.equals(id);}
    private static WeaponSkillKeyframeTimeline timeline(String id){return WHIRL.equals(id)?WHIRL_POSE:LEAP.equals(id)?LEAP_POSE:MALLET_SWING.equals(id)?MALLET_POSE:CLUB_POSE;}
    static WeaponSkillPose sample(String id,float t){return timeline(id).sampleRight(t);}
    @Override public boolean apply(PoseStack stack,HumanoidArm arm,float t){return timeline(skill).apply(stack,arm,t);}
    static void applyBodyPose(HumanoidModel<?> model,LivingEntity actor,String id,float t) {
        HeavyHammerAnimation.applyBodyFromPose(model,actor,sample(id,t),t,WHIRL.equals(id));
    }
    private static WeaponSkillKeyframeTimeline.Keyframe k(float t,WeaponSkillPose p,WeaponSkillKeyframeTimeline.Easing e){return new WeaponSkillKeyframeTimeline.Keyframe(t,p,e);}
}
