package com.stardew.craft.client.weapon.animation;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Vector3f;
import static com.stardew.craft.client.weapon.MeleeWeaponVisuals.*;
import static com.stardew.craft.client.weapon.animation.WeaponSkillKeyframeTimeline.Easing.*;

/** Short upright thrusts for crystal, a hooked draw-cut for venom. */
public final class CrystalVenomAnimation implements WeaponSkillAnimation {
    private static final WeaponSkillPose REST=new WeaponSkillPose(0,-90,25,1.13f,3.2f,1.13f);
    private static final Vector3f GRIP=new Vector3f(0,-.375f,0);
    private static final WeaponSkillKeyframeTimeline CRYSTAL=new WeaponSkillKeyframeTimeline(8,REST,GRIP,
            key(0,REST,LINEAR),key(.45f,new WeaponSkillPose(-23,-81,16,1.2f,3.6f,1.8f),EASE_OUT_CUBIC),
            key(1.48f,new WeaponSkillPose(-42,-78,23,.7f,3.2f,-5.2f),EASE_IN_QUAD),
            key(2.6f,new WeaponSkillPose(-28,-82,19,1.0f,3.4f,-1.4f),EASE_OUT_CUBIC),key(8,REST,SMOOTH));
    private static final WeaponSkillKeyframeTimeline HOOK=new WeaponSkillKeyframeTimeline(6,REST,GRIP,
            key(0,REST,LINEAR),key(.35f,new WeaponSkillPose(-21,-66,8,2.1f,3.9f,.4f),EASE_OUT_CUBIC),
            key(1.11f,new WeaponSkillPose(-34,-61,48,-1.8f,2.5f,-3.7f),EASE_IN_QUAD),
            key(2.2f,new WeaponSkillPose(-15,-82,49,-.3f,3.3f,-1.0f),EASE_OUT_CUBIC),key(6,REST,SMOOTH));
    private static final WeaponSkillKeyframeTimeline NEST=new WeaponSkillKeyframeTimeline(8,REST,GRIP,
            key(0,REST,LINEAR),key(.5f,new WeaponSkillPose(-27,-74,8,1.9f,3.9f,.5f),EASE_OUT_CUBIC),
            key(1.48f,new WeaponSkillPose(-39,-72,33,.1f,2.9f,-5.0f),EASE_IN_QUAD),
            key(3,new WeaponSkillPose(-16,-54,51,-1.8f,3.2f,-.4f),EASE_OUT_CUBIC),key(8,REST,SMOOTH));
    private final String id;
    public CrystalVenomAnimation(String id) {this.id=id;}
    public static boolean supports(String id) {return CRYSTAL_SWING.equals(id)||CRYSTAL_LAYER.equals(id)||VENOM_SWING.equals(id)||VENOM_RIPPLE.equals(id)||VENOM_NEST.equals(id);}
    private static WeaponSkillKeyframeTimeline timeline(String id) {
        return switch(id) {case CRYSTAL_SWING,CRYSTAL_LAYER->CRYSTAL;case VENOM_SWING,VENOM_RIPPLE->HOOK;case VENOM_NEST->NEST;default->throw new IllegalArgumentException(id);};
    }
    static WeaponSkillPose sample(String id,float t) {return timeline(id).sampleRight(t);}
    @Override public boolean apply(PoseStack pose,HumanoidArm arm,float t) {return timeline(id).apply(pose,arm,t);}
    static void applyBodyPose(HumanoidModel<?> model,LivingEntity entity,String id,float t) {
        if(CRYSTAL_SWING.equals(id)||CRYSTAL_LAYER.equals(id)||VENOM_NEST.equals(id)) DragontoothShivAnimation.applyBodyPose(model,entity,SHIV_STAB,t);
        else LavaKatanaSlashAnimation.applyBodyPose(model,entity,t);
    }
    private static WeaponSkillKeyframeTimeline.Keyframe key(float t,WeaponSkillPose p,WeaponSkillKeyframeTimeline.Easing e) {return new WeaponSkillKeyframeTimeline.Keyframe(t,p,e);}
}
