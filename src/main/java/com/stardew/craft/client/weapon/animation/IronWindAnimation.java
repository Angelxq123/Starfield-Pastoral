package com.stardew.craft.client.weapon.animation;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Vector3f;
import static com.stardew.craft.client.weapon.MeleeWeaponVisuals.*;
import static com.stardew.craft.client.weapon.animation.WeaponSkillKeyframeTimeline.Easing.*;

/** Point-first motion: iron retracts tightly; wind follows through before settling. */
public final class IronWindAnimation implements WeaponSkillAnimation {
    private static final WeaponSkillPose REST = new WeaponSkillPose(0,-90,25,1.13f,3.2f,1.13f);
    private static final Vector3f GRIP = new Vector3f(0,-.375f,0);
    private static final WeaponSkillKeyframeTimeline IRON = thrust(false,false), IRON_SKILL = thrust(false,true);
    private static final WeaponSkillKeyframeTimeline WIND = thrust(true,false), WIND_SKILL = thrust(true,true);
    private final String id;
    public IronWindAnimation(String id) { this.id=id; }
    public static boolean supports(String id) {
        return IRON_SWING.equals(id) || IRON_THRUST.equals(id) || WIND_SWING.equals(id) || WIND_THRUST.equals(id);
    }
    private static WeaponSkillKeyframeTimeline timeline(String id) {
        return switch(id) {
            case IRON_SWING -> IRON;
            case IRON_THRUST -> IRON_SKILL;
            case WIND_SWING -> WIND;
            case WIND_THRUST -> WIND_SKILL;
            default -> throw new IllegalArgumentException(id);
        };
    }
    static WeaponSkillPose sample(String id,float t) { return timeline(id).sampleRight(t); }
    @Override public boolean apply(PoseStack stack,HumanoidArm arm,float t) { return timeline(id).apply(stack,arm,t); }
    private static WeaponSkillKeyframeTimeline thrust(boolean wind,boolean skill) {
        return new WeaponSkillKeyframeTimeline(8,REST,GRIP,
                key(0,REST,LINEAR),key(.5f,new WeaponSkillPose(-24,-84,16,1.7f,3.6f,.45f),EASE_OUT_CUBIC),
                key(1.48f,new WeaponSkillPose(wind?-43:-39,-85,wind?12:23,wind?.55f:.9f,3.1f,skill?(wind?-5.5f:-5f):-3.7f),EASE_IN_QUAD),
                key(wind?3.1f:2.5f,new WeaponSkillPose(wind?-31:-21,-83,wind?18:24,wind?.35f:1.35f,wind?3.5f:3.3f,wind?-2.7f:-.85f),EASE_OUT_CUBIC),
                key(8,REST,SMOOTH));
    }
    static void applyBodyPose(HumanoidModel<?> model,LivingEntity entity,String id,float t) {
        boolean wind=WIND_SWING.equals(id)||WIND_THRUST.equals(id);
        float weight=Mth.clamp(t/.065f,0,1)*(1-Mth.clamp((t-.3f)/.7f,0,1));
        float sign=entity.getMainArm()==HumanoidArm.RIGHT?1:-1;
        var arm=sign>0?model.rightArm:model.leftArm;
        arm.xRot=Mth.lerp(weight,arm.xRot,wind?-1.48f:-1.35f);
        arm.yRot=Mth.lerp(weight,arm.yRot,-sign*.08f);
        arm.zRot=Mth.lerp(weight,arm.zRot,sign*.05f);
        arm.z-=weight*(wind?1.9f:1.4f);
        model.body.yRot+=weight*sign*(wind?.1f:.055f);
    }
    private static WeaponSkillKeyframeTimeline.Keyframe key(float t,WeaponSkillPose p,WeaponSkillKeyframeTimeline.Easing e) {
        return new WeaponSkillKeyframeTimeline.Keyframe(t,p,e);
    }
}
