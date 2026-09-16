package com.stardew.craft.client.weapon.animation;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Vector3f;
import static com.stardew.craft.client.weapon.MeleeWeaponVisuals.*;
import static com.stardew.craft.client.weapon.animation.WeaponSkillKeyframeTimeline.Easing.*;

/** Compact point-first attacks; the stance ignition keeps the normal grip. */
public final class DragontoothShivAnimation implements WeaponSkillAnimation {
    private static final WeaponSkillPose REST = new WeaponSkillPose(0, -90, 25, 1.13f, 3.2f, 1.13f);
    private static final Vector3f GRIP = new Vector3f(0, -0.375f, 0);
    private static final WeaponSkillKeyframeTimeline NORMAL = thrust(false);
    private static final WeaponSkillKeyframeTimeline STAB = thrust(true);
    private static final WeaponSkillKeyframeTimeline IGNITE = new WeaponSkillKeyframeTimeline(8, REST, GRIP,
            key(0, REST, LINEAR),
            key(1.5f, new WeaponSkillPose(-16, -86, 20, 1.6f, 4.3f, 0.4f), EASE_OUT_CUBIC),
            key(3, new WeaponSkillPose(-10, -84, 26, 1.3f, 3.7f, -0.8f), EASE_IN_QUAD),
            key(8, REST, SMOOTH));
    private final boolean ignite;
    public DragontoothShivAnimation(boolean ignite) { this.ignite = ignite; }
    @Override public boolean apply(PoseStack stack, HumanoidArm arm, float progress) {
        return (ignite ? IGNITE : STAB).apply(stack, arm, progress);
    }
    public static boolean supports(String skill) {
        return SHIV_SWING.equals(skill) || SHIV_EMPOWERED.equals(skill) || SHIV_STAB.equals(skill) || SHIV_BREATH.equals(skill);
    }
    static WeaponSkillPose sample(String skill, float progress) {
        return (SHIV_BREATH.equals(skill) ? IGNITE : SHIV_STAB.equals(skill) ? STAB : NORMAL).sampleRight(progress);
    }
    private static WeaponSkillKeyframeTimeline thrust(boolean skill) {
        return new WeaponSkillKeyframeTimeline(8, REST, GRIP,
                key(0, REST, LINEAR),
                key(0.35f, new WeaponSkillPose(-69, -86, 18, 1.8f, 2.9f, 0.7f), EASE_OUT_CUBIC),
                key(1.48f, new WeaponSkillPose(-83, -87, 18, 0.6f, 2.5f, skill ? -8.2f : -6.2f), EASE_IN_QUAD),
                key(2.6f, new WeaponSkillPose(-74, -84, 22, 1.5f, 2.8f, -2.2f), EASE_OUT_CUBIC),
                key(5.5f, new WeaponSkillPose(-14, -88, 24, 1.2f, 3.1f, 0.6f), SMOOTH),
                key(8, REST, SMOOTH));
    }
    static void applyBodyPose(HumanoidModel<?> model, LivingEntity entity, String skill, float t) {
        boolean ignition = SHIV_BREATH.equals(skill);
        float weight = Mth.clamp(t / 0.06f, 0, 1) * (1 - Mth.clamp((t - 0.32f) / 0.68f, 0, 1));
        float sign = entity.getMainArm() == HumanoidArm.RIGHT ? 1 : -1;
        var arm = sign > 0 ? model.rightArm : model.leftArm;
        arm.xRot = Mth.lerp(weight, arm.xRot, ignition ? -0.85f : -1.48f);
        arm.yRot = Mth.lerp(weight, arm.yRot, -0.06f * sign);
        arm.zRot = Mth.lerp(weight, arm.zRot, 0.05f * sign);
        if (!ignition) {
            arm.z -= weight * (SHIV_STAB.equals(skill) ? 2.4f : 1.8f);
            model.body.yRot += weight * sign * 0.08f;
        }
    }
    private static WeaponSkillKeyframeTimeline.Keyframe key(float t, WeaponSkillPose p, WeaponSkillKeyframeTimeline.Easing e) {
        return new WeaponSkillKeyframeTimeline.Keyframe(t, p, e);
    }
}
