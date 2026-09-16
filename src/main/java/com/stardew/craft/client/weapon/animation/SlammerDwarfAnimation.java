package com.stardew.craft.client.weapon.animation;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Vector3f;
import static com.stardew.craft.combat.skill.handler.SlammerDwarfRules.*;
import static com.stardew.craft.client.weapon.animation.WeaponSkillKeyframeTimeline.Easing.*;

public final class SlammerDwarfAnimation implements WeaponSkillAnimation {
    private static final WeaponSkillPose REST=new WeaponSkillPose(0,-90,25,1.13f,3.2f,1.13f);
    private static final Vector3f GRIP=new Vector3f(0,-.375f,0);
    private static final WeaponSkillPose HIGH=new WeaponSkillPose(-70,-63,-14,-4.04f,11.49f,-1.30f);
    private static final WeaponSkillPose DOWN=new WeaponSkillPose(35,-36,64,-9.22f,-0.07f,-6.51f);
    private static final WeaponSkillPose LEFT=new WeaponSkillPose(25,-40,74,-10.75f,1.46f,-4.88f),RIGHT=new WeaponSkillPose(20,-46,10,-6.27f,2.01f,-6.65f);
    private static final WeaponSkillPose LOW=new WeaponSkillPose(32,-48,74,-1.03f,0.09f,-2.36f),UP=new WeaponSkillPose(-42,-32,-30,-13.16f,12.43f,-5.69f);
    private static final WeaponSkillPose DRAW=new WeaponSkillPose(-38,-58,-18,-3.73f,7.26f,-1.83f),EXTEND=new WeaponSkillPose(18,-35,54,-8.95f,4.64f,-7.01f),REBOUND=new WeaponSkillPose(-12,-55,12,-4.81f,5.98f,-2.81f);
    private static final WeaponSkillKeyframeTimeline LIFT_POSE=new WeaponSkillKeyframeTimeline(14,REST,GRIP,
            k(0,REST,LINEAR),k(3,LOW,SMOOTH),k(4.1f,LOW,LINEAR),k(6,UP,EASE_IN_QUAD),k(7,UP,LINEAR),k(14,REST,SMOOTH));
    private static final WeaponSkillKeyframeTimeline RUSH_POSE=new WeaponSkillKeyframeTimeline(32,REST,GRIP,
            k(0,REST,LINEAR),k(3,HIGH,SMOOTH),k(4.4f,HIGH,LINEAR),k(6,LEFT,EASE_IN_QUAD),k(7,LEFT,LINEAR),
            k(10,HIGH,SMOOTH),k(12,RIGHT,EASE_IN_QUAD),k(13,RIGHT,LINEAR),
            k(16,HIGH,SMOOTH),k(18,LEFT,EASE_IN_QUAD),k(19,LEFT,LINEAR),
            k(22.5f,HIGH,SMOOTH),k(24.3f,HIGH,LINEAR),k(26,DOWN,EASE_IN_QUAD),k(28,DOWN,LINEAR),k(32,REST,SMOOTH));
    private static final WeaponSkillKeyframeTimeline PISTON_POSE=new WeaponSkillKeyframeTimeline(14,REST,GRIP,
            k(0,REST,LINEAR),k(2.5f,DRAW,SMOOTH),k(3.6f,DRAW,LINEAR),k(5,EXTEND,EASE_IN_QUAD),k(6,EXTEND,LINEAR),
            k(7.5f,REBOUND,SMOOTH),k(9,EXTEND,EASE_IN_QUAD),k(10,EXTEND,LINEAR),k(14,REST,SMOOTH));
    private static final WeaponSkillKeyframeTimeline FAULT_POSE=new WeaponSkillKeyframeTimeline(12,REST,GRIP,
            k(0,REST,LINEAR),k(4.8f,HIGH,SMOOTH),k(6.2f,HIGH,LINEAR),k(8,DOWN,EASE_IN_QUAD),k(9,DOWN,LINEAR),k(12,REST,SMOOTH));
    private static final WeaponSkillKeyframeTimeline SLAMMER_NORMAL=normal(new WeaponSkillPose(-32,-81,12,1.5f,4.6f,.4f),new WeaponSkillPose(-8,-79,40,.1f,.9f,-3.5f)),DWARF_NORMAL=normal(new WeaponSkillPose(-16,-85,24,1.4f,3.3f,.3f),new WeaponSkillPose(-12,-82,27,.5f,2.6f,-4.0f));
    private final String skill;
    public SlammerDwarfAnimation(String skill){this.skill=skill;}
    public static boolean supports(String id){return LIFT.equals(id)||RUSH.equals(id)||PISTON.equals(id)||FAULT.equals(id)||SLAMMER_SWING.equals(id)||DWARF_SWING.equals(id);}
    private static WeaponSkillKeyframeTimeline timeline(String id){return LIFT.equals(id)?LIFT_POSE:RUSH.equals(id)?RUSH_POSE:PISTON.equals(id)?PISTON_POSE:FAULT.equals(id)?FAULT_POSE:SLAMMER_SWING.equals(id)?SLAMMER_NORMAL:DWARF_NORMAL;}
    static WeaponSkillPose sample(String id,float t){return timeline(id).sampleRight(t);}
    @Override public boolean apply(PoseStack stack,HumanoidArm arm,float t){return timeline(skill).apply(stack,arm,t);}
    static void applyBodyPose(HumanoidModel<?> model,LivingEntity actor,String id,float t) {
        if(PISTON.equals(id)) {
            var pose=sample(id,t);
            float extension=Math.max(0,Math.min(1,(-pose.tz()-1.5f)/6));
            float envelope=(float)Math.sin(Math.PI*t);
            var arm=actor.getMainArm()==HumanoidArm.RIGHT?model.rightArm:model.leftArm;
            arm.xRot-=envelope*.8f+extension*.65f;
            model.body.xRot+=extension*.18f;
            return;
        }
        HeavyHammerAnimation.applyBodyFromPose(model,actor,sample(id,t),t,false);
    }
    private static WeaponSkillKeyframeTimeline normal(WeaponSkillPose wind,WeaponSkillPose hit){return new WeaponSkillKeyframeTimeline(7,REST,GRIP,k(0,REST,LINEAR),k(.6f,wind,EASE_OUT_CUBIC),k(1.3f,hit,EASE_IN_QUAD),k(2,hit,LINEAR),k(7,REST,SMOOTH));}
    private static WeaponSkillKeyframeTimeline.Keyframe k(float t,WeaponSkillPose p,WeaponSkillKeyframeTimeline.Easing e){return new WeaponSkillKeyframeTimeline.Keyframe(t,p,e);}
}
