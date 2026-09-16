package com.stardew.craft.client.weapon.animation;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Vector3f;
import static com.stardew.craft.client.weapon.MeleeWeaponVisuals.*;
import static com.stardew.craft.client.weapon.animation.WeaponSkillKeyframeTimeline.Easing.*;

/** Measured blood slash and forge press; neither sustained state holds the wrist in a casting pose. */
public final class BloodForgeAnimation implements WeaponSkillAnimation {
    private static final WeaponSkillPose REST = new WeaponSkillPose(0,-90,25,1.13f,3.2f,1.13f);
    private static final Vector3f GRIP = new Vector3f(0,-0.375f,0);
    private static final WeaponSkillKeyframeTimeline DEBT = new WeaponSkillKeyframeTimeline(8,REST,GRIP,
            key(0,REST,LINEAR),key(.4f,new WeaponSkillPose(-24,-66,7,2.5f,4.1f,.3f),EASE_OUT_CUBIC),
            key(1.48f,new WeaponSkillPose(-34,-51,53,-2.5f,2.6f,-4.7f),EASE_IN_QUAD),
            key(2.6f,new WeaponSkillPose(-21,-53,60,-2.7f,2.8f,-2.4f),EASE_OUT_CUBIC),key(8,REST,SMOOTH));
    private static final WeaponSkillKeyframeTimeline QUENCH = new WeaponSkillKeyframeTimeline(10,REST,GRIP,
            key(0,REST,LINEAR),key(.6f,new WeaponSkillPose(-32,-77,6,1.8f,4.6f,.1f),EASE_OUT_CUBIC),
            key(1.85f,new WeaponSkillPose(-36,-70,50,-1.4f,1.3f,-5.1f),EASE_IN_QUAD),
            key(3,new WeaponSkillPose(-23,-65,53,-1.6f,1.6f,-3.2f),EASE_OUT_CUBIC),key(10,REST,SMOOTH));
    private static final WeaponSkillKeyframeTimeline MOON = new WeaponSkillKeyframeTimeline(10,REST,GRIP,
            key(0,REST,LINEAR),key(2,new WeaponSkillPose(-24,-73,8,1.2f,4.3f,-.8f),EASE_OUT_CUBIC),
            key(3.5f,new WeaponSkillPose(-19,-63,32,-.5f,3.4f,-1.6f),SMOOTH),key(10,REST,SMOOTH));
    private static final WeaponSkillKeyframeTimeline BILLET = new WeaponSkillKeyframeTimeline(12,REST,GRIP,
            key(0,REST,LINEAR),key(.8f,new WeaponSkillPose(-39,-72,18,.7f,3.6f,-2.6f),EASE_OUT_CUBIC),
            key(2.2f,new WeaponSkillPose(-22,-73,29,1.2f,3.8f,.2f),EASE_OUT_CUBIC),key(12,REST,SMOOTH));
    private final String id;
    public BloodForgeAnimation(String id) { this.id=id; }
    public static boolean supports(String id) {
        return DARK_SWING.equals(id)||DARK_DEBT.equals(id)||DARK_MOON.equals(id)
                ||FORGE_SWING.equals(id)||FORGE_QUENCH.equals(id)||FORGE_BILLET.equals(id);
    }
    private static WeaponSkillKeyframeTimeline timeline(String id) {
        return switch(id) { case DARK_DEBT -> DEBT; case FORGE_QUENCH -> QUENCH;
            case DARK_MOON -> MOON; case FORGE_BILLET -> BILLET; default -> throw new IllegalArgumentException(id); };
    }
    static WeaponSkillPose sample(String id,float t) {
        return DARK_SWING.equals(id)||FORGE_SWING.equals(id) ? LavaKatanaSlashAnimation.sample(t) : timeline(id).sampleRight(t);
    }
    @Override public boolean apply(PoseStack pose, HumanoidArm arm,float t) { return timeline(id).apply(pose,arm,t); }
    static void applyBodyPose(HumanoidModel<?> model, LivingEntity entity,String id,float t) {
        if(DARK_MOON.equals(id)||FORGE_BILLET.equals(id)) MeowmereAnimation.applyBodyPose(model,entity,t);
        else LavaKatanaSlashAnimation.applyBodyPose(model,entity,t);
    }
    private static WeaponSkillKeyframeTimeline.Keyframe key(float t,WeaponSkillPose p,WeaponSkillKeyframeTimeline.Easing e) {
        return new WeaponSkillKeyframeTimeline.Keyframe(t,p,e);
    }
}
