package com.stardew.craft.client.weapon.animation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.util.Mth;
import org.joml.Vector3f;
import static com.stardew.craft.client.weapon.MeleeWeaponVisuals.*;
import static com.stardew.craft.client.weapon.animation.WeaponSkillKeyframeTimeline.Easing.*;

public final class GuardSpineAnimation implements WeaponSkillAnimation {
    private static final WeaponSkillPose REST=new WeaponSkillPose(0,-90,25,1.13f,3.2f,1.13f);
    private static final WeaponSkillPose GUARD=new WeaponSkillPose(-18,-77,42,.25f,3.8f,-.3f);
    private static final Vector3f GRIP=new Vector3f(0,-.375f,0);
    private static final WeaponSkillKeyframeTimeline HOLD=new WeaponSkillKeyframeTimeline(20,REST,GRIP,
            k(0,REST,LINEAR),k(2,GUARD,SMOOTH),k(17,GUARD,LINEAR),k(20,REST,SMOOTH));
    private static final WeaponSkillKeyframeTimeline COUNTER=new WeaponSkillKeyframeTimeline(8,REST,GRIP,
            k(0,GUARD,LINEAR),k(.5f,new WeaponSkillPose(-23,-73,38,.65f,3.85f,-.6f),EASE_OUT_CUBIC),
            k(1.48f,new WeaponSkillPose(-35,-82,14,2.1f,2.7f,-4.5f),EASE_IN_QUAD),k(3,new WeaponSkillPose(-20,-85,19,1.8f,3,-1.2f),EASE_OUT_CUBIC),k(8,REST,SMOOTH));
    private static final WeaponSkillKeyframeTimeline LIGHT=cut(false),HEAVY=cut(true);
    private static final WeaponSkillKeyframeTimeline ENTER=new WeaponSkillKeyframeTimeline(8,REST,GRIP,
            k(0,REST,LINEAR),k(2,new WeaponSkillPose(-17,-84,30,.9f,3.8f,-.35f),SMOOTH),k(8,REST,SMOOTH));
    private final String id;
    public GuardSpineAnimation(String id){this.id=id;}
    public static boolean supports(String id){return LIGHT_SWING.equals(id)||LIGHT_GUARD.equals(id)||LIGHT_COUNTER.equals(id)||SPINE_SWING.equals(id)||SPINE_ENTER.equals(id)||SPINE_STRIKE.equals(id)||SPINE_WEAK.equals(id);}
    private static WeaponSkillKeyframeTimeline timeline(String id){return switch(id){case LIGHT_GUARD->HOLD;case LIGHT_COUNTER->COUNTER;case LIGHT_SWING->LIGHT;case SPINE_ENTER->ENTER;case SPINE_SWING,SPINE_STRIKE,SPINE_WEAK->HEAVY;default->throw new IllegalArgumentException(id);};}
    static WeaponSkillPose sample(String id,float t){return timeline(id).sampleRight(t);}
    @Override public boolean apply(PoseStack stack,HumanoidArm arm,float t){return timeline(id).apply(stack,arm,t);}
    private static WeaponSkillKeyframeTimeline cut(boolean heavy){return new WeaponSkillKeyframeTimeline(8,REST,GRIP,
            k(0,REST,LINEAR),k(.5f,new WeaponSkillPose(-24,-69,12,2.1f,heavy?4.55f:3.7f,.15f),EASE_OUT_CUBIC),
            k(1.48f,new WeaponSkillPose(heavy?-42:-28,-82,44,heavy?-1.9f:-1.2f,heavy?2.1f:2.9f,heavy?-4.1f:-3.1f),EASE_IN_QUAD),
            k(heavy?3.9f:2.6f,new WeaponSkillPose(-20,-85,34,-.2f,2.9f,-1.1f),EASE_OUT_CUBIC),k(8,REST,SMOOTH));}
    static void applyBodyPose(HumanoidModel<?> model,LivingEntity entity,String id,float t){
        float sign=entity.getMainArm()==HumanoidArm.RIGHT?1:-1;
        var arm=sign>0?model.rightArm:model.leftArm;
        boolean guard=LIGHT_GUARD.equals(id);float weight=guard?Mth.clamp(t/.1f,0,1)*(1-Mth.clamp((t-.85f)/.15f,0,1)):Mth.clamp(t/.065f,0,1)*(1-Mth.clamp((t-.4f)/.6f,0,1));
        arm.xRot=Mth.lerp(weight,arm.xRot,guard?-1.1f:-1.35f);arm.yRot=Mth.lerp(weight,arm.yRot,sign*(guard?-.22f:.18f));arm.zRot=Mth.lerp(weight,arm.zRot,sign*.16f);
        if(!guard)model.body.yRot+=sign*weight*(2*Mth.clamp(t/.185f,0,1)-1)*.14f;
    }
    private static WeaponSkillKeyframeTimeline.Keyframe k(float t,WeaponSkillPose p,WeaponSkillKeyframeTimeline.Easing e){return new WeaponSkillKeyframeTimeline.Keyframe(t,p,e);}
}
