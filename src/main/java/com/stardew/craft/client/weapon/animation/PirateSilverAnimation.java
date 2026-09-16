package com.stardew.craft.client.weapon.animation;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Vector3f;
import static com.stardew.craft.client.weapon.MeleeWeaponVisuals.*;
import static com.stardew.craft.client.weapon.animation.WeaponSkillKeyframeTimeline.Easing.*;

/** Low pirate draw-cuts and opposing saber cuts, each ending at a stable grip. */
public final class PirateSilverAnimation implements WeaponSkillAnimation {
    private static final WeaponSkillPose REST=new WeaponSkillPose(0,-90,25,1.13f,3.2f,1.13f);
    private static final Vector3f GRIP=new Vector3f(0,-.375f,0);
    private static final WeaponSkillKeyframeTimeline PIRATE=cut(true,false,false),PLUNDER=cut(true,true,false);
    private static final WeaponSkillKeyframeTimeline SILVER=cut(false,false,false),OUT=cut(false,true,false),BACK=cut(false,true,true);
    private static final WeaponSkillKeyframeTimeline DASH=new WeaponSkillKeyframeTimeline(8,REST,GRIP,
            key(0,REST,LINEAR),key(.7f,new WeaponSkillPose(-23,-83,16,1.4f,3.65f,-.5f),EASE_OUT_CUBIC),
            key(2,new WeaponSkillPose(-33,-85,15,.75f,3.15f,-3.8f),EASE_IN_QUAD),key(8,REST,SMOOTH));
    private final String id;
    public PirateSilverAnimation(String id){this.id=id;}
    public static boolean supports(String id){return PIRATE_SWING.equals(id)||PIRATE_PLUNDER.equals(id)||SILVER_SWING.equals(id)||SILVER_OUT.equals(id)||SILVER_RETURN.equals(id)||SILVER_STAY.equals(id)||SILVER_EMPTY.equals(id);}
    private static WeaponSkillKeyframeTimeline timeline(String id){return switch(id){
        case PIRATE_SWING->PIRATE;case PIRATE_PLUNDER->PLUNDER;case SILVER_SWING->SILVER;
        case SILVER_OUT->OUT;case SILVER_RETURN,SILVER_STAY->BACK;case SILVER_EMPTY->DASH;default->throw new IllegalArgumentException(id);};}
    static WeaponSkillPose sample(String id,float t){return timeline(id).sampleRight(t);}
    @Override public boolean apply(PoseStack stack,HumanoidArm arm,float t){return timeline(id).apply(stack,arm,t);}
    private static WeaponSkillKeyframeTimeline cut(boolean pirate,boolean skill,boolean back){
        return new WeaponSkillKeyframeTimeline(8,REST,GRIP,
                key(0,REST,LINEAR),key(.5f,new WeaponSkillPose(-26,back?-80:-66,back?47:8,back?-1.1f:2.7f,pirate?2.25f:4.05f,.2f),EASE_OUT_CUBIC),
                key(1.48f,new WeaponSkillPose(pirate?-38:-28,back?-64:-75,back?7:49,back?3.1f:skill?-2.7f:-1.8f,pirate?2.9f:2.5f,skill?-4.4f:-3.2f),EASE_IN_QUAD),
                key(pirate?3.2f:2.6f,new WeaponSkillPose(-21,-79,back?17:42,back?2.1f:-.9f,3.1f,-1.4f),EASE_OUT_CUBIC),key(8,REST,SMOOTH));
    }
    static void applyBodyPose(HumanoidModel<?> model,LivingEntity entity,String id,float t){
        boolean back=SILVER_RETURN.equals(id)||SILVER_STAY.equals(id),pirate=PIRATE_SWING.equals(id)||PIRATE_PLUNDER.equals(id);
        float weight=Mth.clamp(t/.065f,0,1)*(1-Mth.clamp((t-.36f)/.64f,0,1));
        float cross=Mth.clamp((t-.065f)/.12f,0,1),sign=entity.getMainArm()==HumanoidArm.RIGHT?1:-1;
        var arm=sign>0?model.rightArm:model.leftArm;
        arm.xRot=Mth.lerp(weight,arm.xRot,pirate?-.95f:-1.25f);
        arm.yRot=Mth.lerp(weight,arm.yRot,sign*(back?-1:1)*Mth.lerp(cross,-.45f,.28f));
        arm.zRot=Mth.lerp(weight,arm.zRot,sign*.13f);
        model.body.yRot+=weight*sign*(back?-1:1)*(2*cross-1)*.13f;
    }
    private static WeaponSkillKeyframeTimeline.Keyframe key(float t,WeaponSkillPose p,WeaponSkillKeyframeTimeline.Easing e){return new WeaponSkillKeyframeTimeline.Keyframe(t,p,e);}
}
