package com.stardew.craft.client.weapon.animation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Vector3f;
import static com.stardew.craft.combat.skill.handler.DragonRapierRules.*;
import static com.stardew.craft.client.weapon.animation.WeaponSkillKeyframeTimeline.Easing.*;
public final class DragonRapierAnimation implements WeaponSkillAnimation {
    private static final WeaponSkillPose REST=new WeaponSkillPose(0,-90,25,1.13f,3.2f,1.13f);
    private static final Vector3f GRIP=new Vector3f(0,-.375f,0);
    private static final WeaponSkillPose HIGH=new WeaponSkillPose(-62,-58,-28,-2.45f,11.10f,-2.42f),HIT=new WeaponSkillPose(28,-34,82,-12.32f,0.90f,-5.02f);
    private static final WeaponSkillPose LOW=new WeaponSkillPose(36,-40,64,-8.60f,0.40f,-6.51f),CHANNEL=new WeaponSkillPose(12,-52,44,-5.34f,2.98f,-4.77f);
    private static final WeaponSkillPose GUARD=new WeaponSkillPose(-18,-55,-12,-4.23f,7.06f,-3.42f),THRUST=new WeaponSkillPose(-42,-40,25,-9.32f,6.24f,-6.07f);
    private static final WeaponSkillKeyframeTimeline JAW_POSE=new WeaponSkillKeyframeTimeline(16,REST,GRIP,k(0,REST,LINEAR),k(4.2f,HIGH,SMOOTH),k(5.3f,HIGH,LINEAR),k(7,HIT,EASE_IN_QUAD),k(9,HIT,LINEAR),k(16,REST,SMOOTH));
    private static final WeaponSkillKeyframeTimeline BREATH_POSE=new WeaponSkillKeyframeTimeline(28,REST,GRIP,k(0,REST,LINEAR),k(5,HIGH,SMOOTH),k(6.3f,HIGH,LINEAR),k(8,LOW,EASE_IN_QUAD),k(10,LOW,LINEAR),k(12,CHANNEL,SMOOTH),k(24,CHANNEL,LINEAR),k(28,REST,SMOOTH));
    private static final WeaponSkillKeyframeTimeline RIPOSTE_POSE=new WeaponSkillKeyframeTimeline(12,REST,GRIP,k(0,REST,LINEAR),k(1,GUARD,EASE_OUT_CUBIC),k(4,GUARD,LINEAR),k(6,THRUST,EASE_IN_QUAD),k(7,THRUST,LINEAR),k(12,REST,SMOOTH));
    private static final WeaponSkillKeyframeTimeline CLUB_NORMAL=normal(new WeaponSkillPose(-30,-80,12,2.2f,4.6f,.2f),new WeaponSkillPose(-12,-79,46,-1.1f,1.4f,-3.5f)),RAPIER_NORMAL=normal(new WeaponSkillPose(-5,-87,25,1.2f,3.3f,.2f),new WeaponSkillPose(-12,-87,23,.35f,3f,-4.4f));
    private final String skill;
    public DragonRapierAnimation(String skill){this.skill=skill;}
    public static boolean supports(String id){return JAW.equals(id)||BREATH.equals(id)||RIPOSTE.equals(id)||CLUB_SWING.equals(id)||RAPIER_SWING.equals(id);}
    private static WeaponSkillKeyframeTimeline timeline(String id){return JAW.equals(id)?JAW_POSE:BREATH.equals(id)?BREATH_POSE:RIPOSTE.equals(id)?RIPOSTE_POSE:CLUB_SWING.equals(id)?CLUB_NORMAL:RAPIER_NORMAL;}
    static WeaponSkillPose sample(String id,float t){return timeline(id).sampleRight(t);}
    @Override public boolean apply(PoseStack stack,HumanoidArm arm,float t){return timeline(skill).apply(stack,arm,t);}
    static void applyBodyPose(HumanoidModel<?> model,LivingEntity actor,String id,float t) {
        if(JAW.equals(id)||BREATH.equals(id)||CLUB_SWING.equals(id)) {
            HeavyHammerAnimation.applyBodyFromPose(model,actor,sample(id,t),t,JAW.equals(id));
            return;
        }
        var p=sample(id,t);float envelope=(float)Math.sin(Math.PI*t);
        var arm=actor.getMainArm()==HumanoidArm.RIGHT?model.rightArm:model.leftArm;
        float extension=Math.max(0,1.13f-p.tz())/7.6f;
        arm.xRot-=envelope*.25f+extension*.75f;model.body.xRot+=extension*.09f;
        model.body.yRot+=(p.tx()-1.13f)*envelope*.07f;
    }
    private static WeaponSkillKeyframeTimeline normal(WeaponSkillPose wind,WeaponSkillPose hit){return new WeaponSkillKeyframeTimeline(7,REST,GRIP,k(0,REST,LINEAR),k(.6f,wind,EASE_OUT_CUBIC),k(1.3f,hit,EASE_IN_QUAD),k(2,hit,LINEAR),k(7,REST,SMOOTH));}
    private static WeaponSkillKeyframeTimeline.Keyframe k(float t,WeaponSkillPose p,WeaponSkillKeyframeTimeline.Easing e){return new WeaponSkillKeyframeTimeline.Keyframe(t,p,e);}
}
