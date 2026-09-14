package com.stardew.craft.client.weapon.animation;

import com.mojang.blaze3d.vertex.PoseStack;
import com.stardew.craft.client.weapon.MeleeWeaponVisuals;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Vector3f;
import static com.stardew.craft.client.weapon.animation.WeaponSkillKeyframeTimeline.Easing.*;

public final class YetiToothAnimation implements WeaponSkillAnimation {
    private static final WeaponSkillPose REST = new WeaponSkillPose(0, -90, 25, 1.13f, 3.2f, 1.13f);
    private static final Vector3f GRIP = new Vector3f(0, -0.375f, 0);
    private static final WeaponSkillKeyframeTimeline MARK = new WeaponSkillKeyframeTimeline(8, REST, GRIP,
            key(0, REST, LINEAR),
            key(0.4f, new WeaponSkillPose(-40, -64, 10, 2.8f, 3.8f, 0.4f), EASE_OUT_CUBIC),
            key(1.45f, new WeaponSkillPose(-58, -40, 55, -2.0f, 2.4f, -4.5f), EASE_IN_QUAD),
            key(3, new WeaponSkillPose(-46, -34, 72, -3.6f, 2.0f, -2.3f), EASE_OUT_CUBIC),
            key(8, REST, SMOOTH));
    private static final WeaponSkillKeyframeTimeline SPINE = new WeaponSkillKeyframeTimeline(8, REST, GRIP,
            key(0, REST, LINEAR),
            key(0.5f, new WeaponSkillPose(-18, -86, 22, 1.8f, 4.4f, 0.6f), EASE_OUT_CUBIC),
            key(1.4f, new WeaponSkillPose(16, -80, 36, 0.1f, 0.6f, -4.0f), EASE_IN_QUAD),
            key(2.6f, new WeaponSkillPose(16, -80, 36, 0.1f, 0.6f, -4.0f), LINEAR),
            key(8, REST, SMOOTH));
    private final boolean spine;
    public YetiToothAnimation(boolean spine) { this.spine = spine; }
    @Override public boolean apply(PoseStack stack, HumanoidArm arm, float progress) {
        return (spine ? SPINE : MARK).apply(stack, arm, progress);
    }
    static WeaponSkillPose sample(String skill, float progress) {
        return (MeleeWeaponVisuals.YETI_SPINE.equals(skill) ? SPINE : MARK).sampleRight(progress);
    }
    static void applyBodyPose(HumanoidModel<?> model, LivingEntity entity, String skill, float t) {
        if (MeleeWeaponVisuals.YETI_MARK.equals(skill)) {
            LavaKatanaSlashAnimation.applyBodyPose(model, entity, t);
            return;
        }
        float weight = Mth.clamp(t / 0.06f, 0, 1) * (1 - Mth.clamp((t - 0.4f) / 0.6f, 0, 1));
        float strike = Mth.clamp(t / 0.18f, 0, 1);
        float mirror = entity.getMainArm() == HumanoidArm.RIGHT ? 1 : -1;
        var main = mirror > 0 ? model.rightArm : model.leftArm;
        main.xRot = Mth.lerp(weight, main.xRot, Mth.lerp(strike, -1.3f, -0.45f));
        main.yRot = Mth.lerp(weight, main.yRot, -0.12f * mirror);
        model.body.xRot += weight * strike * 0.16f;
    }
    private static WeaponSkillKeyframeTimeline.Keyframe key(float tick, WeaponSkillPose pose,
                                                            WeaponSkillKeyframeTimeline.Easing easing) {
        return new WeaponSkillKeyframeTimeline.Keyframe(tick, pose, easing);
    }
}
