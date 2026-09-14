package com.stardew.craft.client.weapon.animation;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Vector3f;
import static com.stardew.craft.client.weapon.MeleeWeaponVisuals.*;
import static com.stardew.craft.client.weapon.animation.WeaponSkillKeyframeTimeline.Easing.*;

public final class InfinityWeaponAnimation implements WeaponSkillAnimation {
    private static final WeaponSkillPose REST = new WeaponSkillPose(0, -90, 25, 1.13f, 3.2f, 1.13f);
    private static final WeaponSkillPose READY = new WeaponSkillPose(-72, -85, 12, 1.8f, 2.9f, 0.2f);
    private static final Vector3f GRIP = new Vector3f(0, -0.375f, 0);
    private static final WeaponSkillKeyframeTimeline EVOLVE = new WeaponSkillKeyframeTimeline(8, REST, GRIP,
            key(0, REST, LINEAR), key(1.8f, new WeaponSkillPose(-15, -78, 12, 1.8f, 4.2f, 0.2f), EASE_OUT_CUBIC),
            key(3.5f, new WeaponSkillPose(-9, -80, 19, 1.4f, 3.8f, -0.5f), SMOOTH), key(8, REST, SMOOTH));
    private static final WeaponSkillKeyframeTimeline RELEASE = new WeaponSkillKeyframeTimeline(8, REST, GRIP,
            key(0, REST, LINEAR), key(0.35f, new WeaponSkillPose(-33, -63, 3, 2.7f, 3.8f, 0.6f), EASE_OUT_CUBIC),
            key(1.48f, new WeaponSkillPose(-51, -41, 61, -2.9f, 1.5f, -5.9f), EASE_IN_QUAD),
            key(3, new WeaponSkillPose(-36, -38, 72, -3.6f, 1.8f, -2.9f), EASE_OUT_CUBIC), key(8, REST, SMOOTH));
    private static final WeaponSkillKeyframeTimeline COLLAPSE = new WeaponSkillKeyframeTimeline(12, REST, GRIP,
            key(0, REST, LINEAR), key(2, new WeaponSkillPose(-18, -78, 17, 2.0f, 4.5f, 0.2f), EASE_OUT_CUBIC),
            key(3.3f, new WeaponSkillPose(10, -73, 31, 0.3f, 1.4f, -3.1f), EASE_IN_QUAD),
            key(5, new WeaponSkillPose(10, -73, 31, 0.3f, 1.4f, -3.1f), LINEAR), key(12, REST, SMOOTH));
    private static final WeaponSkillKeyframeTimeline PREPARE = new WeaponSkillKeyframeTimeline(6, REST, GRIP,
            key(0, REST, LINEAR), key(0.4f, READY, EASE_OUT_CUBIC), key(2, READY, LINEAR), key(6, REST, SMOOTH));
    private static final WeaponSkillKeyframeTimeline STAB = new WeaponSkillKeyframeTimeline(4, REST, GRIP,
            key(0, READY, LINEAR), key(0.74f, new WeaponSkillPose(-88, -86, 10, 0.6f, 2.6f, -7.2f), EASE_IN_QUAD),
            key(1.6f, READY, EASE_OUT_CUBIC), key(2, READY, LINEAR), key(4, REST, SMOOTH));
    // Both server hits are simultaneous: one committed puncture followed by a diagonal withdrawal.
    private static final WeaponSkillKeyframeTimeline BACK = new WeaponSkillKeyframeTimeline(8, REST, GRIP,
            key(0, REST, LINEAR), key(0.35f, READY, EASE_OUT_CUBIC),
            key(1.48f, new WeaponSkillPose(-88, -85, 9, 0.1f, 2.5f, -9.2f), EASE_IN_QUAD),
            key(2.7f, new WeaponSkillPose(-65, -63, 39, 3.0f, 2.6f, -4.0f), EASE_OUT_CUBIC), key(8, REST, SMOOTH));
    private final String skill;
    public InfinityWeaponAnimation(String skill) { this.skill = skill; }
    private static WeaponSkillKeyframeTimeline timeline(String id) {
        return switch (id) {
            case INFINITY_EVOLVE -> EVOLVE;
            case INFINITY_RELEASE -> RELEASE;
            case INFINITY_COLLAPSE -> COLLAPSE;
            case INFINITY_READY -> PREPARE;
            case INFINITY_STAB -> STAB;
            case INFINITY_BACK -> BACK;
            default -> throw new IllegalArgumentException("Not an infinity action: " + id);
        };
    }
    @Override public boolean apply(PoseStack stack, HumanoidArm hand, float t) { return timeline(skill).apply(stack, hand, t); }
    static WeaponSkillPose sample(String id, float t) { return timeline(id).sampleRight(t); }
    static void applyBodyPose(HumanoidModel<?> model, LivingEntity entity, String id, float t) {
        if (INFINITY_RELEASE.equals(id)) { LavaKatanaSlashAnimation.applyBodyPose(model, entity, t); return; }
        float w = Mth.clamp(t / 0.07f, 0, 1) * (1 - Mth.clamp((t - 0.4f) / 0.6f, 0, 1));
        float sign = entity.getMainArm() == HumanoidArm.RIGHT ? 1 : -1;
        var arm = sign > 0 ? model.rightArm : model.leftArm;
        boolean dagger = INFINITY_STAB.equals(id) || INFINITY_BACK.equals(id);
        arm.xRot = Mth.lerp(w, arm.xRot, dagger ? -1.5f : INFINITY_COLLAPSE.equals(id) ? -0.9f : -1.0f);
        arm.yRot = Mth.lerp(w, arm.yRot, -0.08f * sign);
        arm.zRot = Mth.lerp(w, arm.zRot, 0.07f * sign);
        if (dagger) { arm.z -= w * 2.5f; model.body.yRot += sign * w * 0.07f; }
    }
    private static WeaponSkillKeyframeTimeline.Keyframe key(float t, WeaponSkillPose p, WeaponSkillKeyframeTimeline.Easing e) {
        return new WeaponSkillKeyframeTimeline.Keyframe(t, p, e);
    }
}
