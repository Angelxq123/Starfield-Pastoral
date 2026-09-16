package com.stardew.craft.client.weapon.animation;

import com.mojang.blaze3d.vertex.PoseStack;
import com.stardew.craft.client.weapon.MeleeWeaponVisuals;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Vector3f;
import static com.stardew.craft.client.weapon.animation.WeaponSkillKeyframeTimeline.Easing.*;

/** A committed forward thrust and a separate diagonal execution cut. */
public final class DragonCutlassAnimation implements WeaponSkillAnimation {
    private static final WeaponSkillPose REST = new WeaponSkillPose(0, -90, 25, 1.13f, 3.2f, 1.13f);
    private static final Vector3f GRIP = new Vector3f(0, -0.375f, 0);
    private static final WeaponSkillKeyframeTimeline THRUST = new WeaponSkillKeyframeTimeline(8, REST, GRIP,
            key(0, REST, LINEAR),
            key(0.45f, new WeaponSkillPose(-72, -82, 12, 2.1f, 3.0f, 0.7f), EASE_OUT_CUBIC),
            key(1.45f, new WeaponSkillPose(-84, -86, 12, 0.7f, 2.7f, -8.0f), EASE_IN_QUAD),
            key(3.8f, new WeaponSkillPose(-78, -82, 18, 1.4f, 2.9f, -5.2f), EASE_OUT_CUBIC),
            key(8, REST, SMOOTH));
    private static final WeaponSkillKeyframeTimeline JUDGEMENT = new WeaponSkillKeyframeTimeline(8, REST, GRIP,
            key(0, REST, LINEAR),
            key(0.45f, new WeaponSkillPose(-62, -36, -24, 3.5f, 4.3f, 0.3f), EASE_OUT_CUBIC),
            key(1.45f, new WeaponSkillPose(-75, -12, 62, -3.4f, 1.1f, -5.1f), EASE_IN_QUAD),
            key(2.8f, new WeaponSkillPose(-58, 6, 94, -6.4f, 0.8f, -2.0f), EASE_OUT_CUBIC),
            key(8, REST, SMOOTH));
    private final boolean judgement;
    public DragonCutlassAnimation(boolean judgement) { this.judgement = judgement; }

    @Override
    public boolean apply(PoseStack stack, HumanoidArm arm, float progress) {
        return (judgement ? JUDGEMENT : THRUST).apply(stack, arm, progress);
    }
    static WeaponSkillPose sample(String skill, float progress) {
        return (MeleeWeaponVisuals.DRAGON_JUDGEMENT.equals(skill) ? JUDGEMENT : THRUST).sampleRight(progress);
    }
    static void applyBodyPose(HumanoidModel<?> model, LivingEntity entity, String skill, float t) {
        if (MeleeWeaponVisuals.DRAGON_JUDGEMENT.equals(skill)) {
            LavaKatanaSlashAnimation.applyBodyPose(model, entity, t);
            return;
        }
        float weight = Mth.clamp(t / 0.06f, 0, 1) * (1 - Mth.clamp((t - 0.5f) / 0.5f, 0, 1));
        float mirror = entity.getMainArm() == HumanoidArm.RIGHT ? 1 : -1;
        var main = mirror > 0 ? model.rightArm : model.leftArm;
        main.xRot = Mth.lerp(weight, main.xRot, -1.48f);
        main.yRot = Mth.lerp(weight, main.yRot, -0.08f * mirror);
        main.z -= weight * 3;
        model.body.yRot += weight * 0.12f * mirror;
        model.body.xRot += weight * 0.12f;
    }
    private static WeaponSkillKeyframeTimeline.Keyframe key(float time, WeaponSkillPose pose,
                                                            WeaponSkillKeyframeTimeline.Easing easing) {
        return new WeaponSkillKeyframeTimeline.Keyframe(time, pose, easing);
    }
}
