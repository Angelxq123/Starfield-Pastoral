package com.stardew.craft.client.weapon.animation;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Vector3f;
import static com.stardew.craft.client.weapon.MeleeWeaponVisuals.*;
import static com.stardew.craft.client.weapon.animation.WeaponSkillKeyframeTimeline.Easing.*;

/** A steady casting blade; the trident punctures forward, then hooks back toward the shoulder. */
public final class TideWeaponAnimation implements WeaponSkillAnimation {
    private static final WeaponSkillPose REST = new WeaponSkillPose(0, -90, 25, 1.13f, 3.2f, 1.13f);
    private static final WeaponSkillPose READY = new WeaponSkillPose(-74, -83, 14, 1.9f, 2.8f, 0.2f);
    private static final Vector3f GRIP = new Vector3f(0, -0.375f, 0);
    private static final WeaponSkillKeyframeTimeline MARK = new WeaponSkillKeyframeTimeline(8, REST, GRIP,
            key(0, REST, LINEAR), key(1.3f, new WeaponSkillPose(-17, -80, 18, 1.0f, 3.7f, -1.8f), EASE_OUT_CUBIC),
            key(2.6f, new WeaponSkillPose(-12, -76, 22, 0.8f, 3.5f, -2.4f), SMOOTH), key(8, REST, SMOOTH));
    private static final WeaponSkillKeyframeTimeline ANCHOR = new WeaponSkillKeyframeTimeline(12, REST, GRIP,
            key(0, REST, LINEAR), key(0.45f, new WeaponSkillPose(-28, -71, 9, 2.8f, 4.0f, 0.8f), EASE_OUT_CUBIC),
            key(1.7f, new WeaponSkillPose(-40, -60, 42, -1.3f, 2.6f, -5.7f), EASE_IN_QUAD),
            key(3.6f, new WeaponSkillPose(-23, -58, 53, -2.1f, 2.1f, -3.4f), EASE_OUT_CUBIC),
            key(5.5f, new WeaponSkillPose(-13, -73, 38, -0.8f, 2.6f, -1.7f), SMOOTH), key(12, REST, SMOOTH));
    private static final WeaponSkillKeyframeTimeline PREPARE = new WeaponSkillKeyframeTimeline(6, REST, GRIP,
            key(0, REST, LINEAR), key(0.5f, READY, EASE_OUT_CUBIC), key(2, READY, LINEAR), key(6, REST, SMOOTH));
    private static final WeaponSkillKeyframeTimeline STAB = new WeaponSkillKeyframeTimeline(6, REST, GRIP,
            key(0, READY, LINEAR), key(1.11f, new WeaponSkillPose(-87, -86, 12, 0.4f, 2.5f, -7.9f), EASE_IN_QUAD),
            key(2.5f, READY, EASE_OUT_CUBIC), key(3, READY, LINEAR), key(6, REST, SMOOTH));
    private static final WeaponSkillKeyframeTimeline REEL = new WeaponSkillKeyframeTimeline(12, REST, GRIP,
            key(0, REST, LINEAR), key(0.5f, READY, EASE_OUT_CUBIC),
            key(1.15f, new WeaponSkillPose(-84, -85, 15, 0.2f, 2.4f, -7.4f), EASE_IN_QUAD),
            key(2.22f, new WeaponSkillPose(-66, -65, 38, 3.0f, 3.3f, -2.0f), EASE_OUT_CUBIC),
            key(4.3f, new WeaponSkillPose(-45, -67, 38, 3.4f, 3.4f, -0.5f), SMOOTH), key(12, REST, SMOOTH));
    private final String skill;
    public TideWeaponAnimation(String skill) { this.skill = skill; }
    private static WeaponSkillKeyframeTimeline timeline(String id) {
        return switch (id) {
            case TIDE_MARK -> MARK;
            case TIDE_ANCHOR -> ANCHOR;
            case TIDE_READY -> PREPARE;
            case TIDE_STAB -> STAB;
            case TIDE_REEL -> REEL;
            default -> throw new IllegalArgumentException("Not a tide action: " + id);
        };
    }
    @Override public boolean apply(PoseStack stack, HumanoidArm hand, float t) { return timeline(skill).apply(stack, hand, t); }
    static WeaponSkillPose sample(String id, float t) { return timeline(id).sampleRight(t); }
    static void applyBodyPose(HumanoidModel<?> model, LivingEntity entity, String id, float t) {
        float w = Mth.clamp(t / 0.08f, 0, 1) * (1 - Mth.clamp((t - 0.3f) / 0.7f, 0, 1));
        float sign = entity.getMainArm() == HumanoidArm.RIGHT ? 1 : -1;
        var arm = sign > 0 ? model.rightArm : model.leftArm;
        boolean stab = TIDE_STAB.equals(id) || TIDE_READY.equals(id);
        float withdraw = TIDE_REEL.equals(id) ? Mth.clamp((t - 0.10f) / 0.20f, 0, 1) : 0;
        arm.xRot = Mth.lerp(w, arm.xRot, stab ? -1.5f : TIDE_REEL.equals(id) ? -1.55f + withdraw * 0.6f : -1.1f);
        arm.yRot = Mth.lerp(w, arm.yRot, sign * (-0.08f + withdraw * 0.23f));
        arm.zRot = Mth.lerp(w, arm.zRot, sign * 0.08f);
        arm.z -= w * (2.0f - withdraw * 2.8f);
        model.body.yRot += sign * w * (TIDE_ANCHOR.equals(id) ? -0.10f : withdraw * 0.12f);
    }
    private static WeaponSkillKeyframeTimeline.Keyframe key(float t, WeaponSkillPose p, WeaponSkillKeyframeTimeline.Easing e) {
        return new WeaponSkillKeyframeTimeline.Keyframe(t, p, e);
    }
}
