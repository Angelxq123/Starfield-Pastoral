package com.stardew.craft.client.weapon.animation;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Vector3f;
import static com.stardew.craft.client.weapon.MeleeWeaponVisuals.*;
import static com.stardew.craft.client.weapon.animation.WeaponSkillKeyframeTimeline.Easing.*;

/** Upright grip, quick contact, measured recovery; the vow holds a readable guard. */
public final class SacredWeaponAnimation implements WeaponSkillAnimation {
    private static final WeaponSkillPose REST = new WeaponSkillPose(0, -90, 25, 1.13f, 3.2f, 1.13f);
    private static final WeaponSkillPose GUARD = new WeaponSkillPose(-20, -70, 8, 0.35f, 4.25f, -0.3f);
    private static final Vector3f GRIP = new Vector3f(0, -0.375f, 0);
    private static final WeaponSkillKeyframeTimeline SMITE = new WeaponSkillKeyframeTimeline(8, REST, GRIP,
            key(0, REST, LINEAR), key(0.38f, new WeaponSkillPose(-27, -68, 4, 2.7f, 4.3f, 0.4f), EASE_OUT_CUBIC),
            key(1.48f, new WeaponSkillPose(-39, -54, 58, -2.6f, 1.9f, -5.5f), EASE_IN_QUAD),
            key(2.5f, new WeaponSkillPose(-25, -49, 64, -3.1f, 2.0f, -3.0f), EASE_OUT_CUBIC), key(8, REST, SMOOTH));
    private static final WeaponSkillKeyframeTimeline NORMAL = new WeaponSkillKeyframeTimeline(6, REST, GRIP,
            key(0, REST, LINEAR), key(0.28f, new WeaponSkillPose(-18, -73, 10, 2.2f, 3.9f, 0.6f), EASE_OUT_CUBIC),
            key(1.11f, new WeaponSkillPose(-28, -60, 51, -1.9f, 2.4f, -4.2f), EASE_IN_QUAD),
            key(2.4f, new WeaponSkillPose(-18, -59, 57, -2.1f, 2.6f, -1.7f), EASE_OUT_CUBIC), key(6, REST, SMOOTH));
    private static final WeaponSkillKeyframeTimeline VOW = new WeaponSkillKeyframeTimeline(42, REST, GRIP,
            key(0, REST, LINEAR), key(3, GUARD, EASE_OUT_CUBIC), key(41, GUARD, LINEAR), key(42, REST, SMOOTH));
    private static final WeaponSkillKeyframeTimeline COUNTER = new WeaponSkillKeyframeTimeline(8, REST, GRIP,
            key(0, GUARD, LINEAR), key(1.48f, new WeaponSkillPose(-31, -48, 61, -2.9f, 2.7f, -5.8f), EASE_IN_QUAD),
            key(2.5f, new WeaponSkillPose(-22, -48, 66, -3.1f, 2.8f, -3.0f), EASE_OUT_CUBIC), key(8, REST, SMOOTH));
    private static final WeaponSkillKeyframeTimeline END = new WeaponSkillKeyframeTimeline(4, REST, GRIP,
            key(0, GUARD, LINEAR), key(4, REST, SMOOTH));
    private static final WeaponSkillKeyframeTimeline DOMAIN = new WeaponSkillKeyframeTimeline(10, REST, GRIP,
            key(0, REST, LINEAR), key(1.8f, GUARD, EASE_OUT_CUBIC),
            key(3.2f, new WeaponSkillPose(-14, -69, 33, 0.5f, 2.35f, -2.4f), EASE_IN_QUAD), key(10, REST, SMOOTH));
    private static final WeaponSkillKeyframeTimeline JUDGEMENT = new WeaponSkillKeyframeTimeline(10, REST, GRIP,
            key(0, REST, LINEAR), key(2, new WeaponSkillPose(-30, -76, 9, 1.8f, 4.7f, -0.6f), EASE_OUT_CUBIC),
            key(3.5f, new WeaponSkillPose(-34, -70, 18, 1.1f, 4.1f, -2.0f), SMOOTH), key(10, REST, SMOOTH));
    private final String skill;
    public SacredWeaponAnimation(String skill) { this.skill = skill; }
    private static WeaponSkillKeyframeTimeline timeline(String id) {
        return switch (id) {
            case HOLY_SWING, TEMPLAR_SWING -> NORMAL;
            case HOLY_SMITE -> SMITE;
            case HOLY_DOMAIN -> DOMAIN;
            case TEMPLAR_VOW -> VOW;
            case TEMPLAR_STRIKE -> COUNTER;
            case TEMPLAR_END -> END;
            case TEMPLAR_JUDGEMENT -> JUDGEMENT;
            default -> throw new IllegalArgumentException("Not a sacred action: " + id);
        };
    }
    @Override public boolean apply(PoseStack stack, HumanoidArm hand, float t) { return timeline(skill).apply(stack, hand, t); }
    static WeaponSkillPose sample(String id, float t) { return timeline(id).sampleRight(t); }
    static void applyBodyPose(HumanoidModel<?> model, LivingEntity entity, String id, float t) {
        boolean guard = TEMPLAR_VOW.equals(id);
        boolean slash = HOLY_SMITE.equals(id) || TEMPLAR_STRIKE.equals(id) || HOLY_SWING.equals(id) || TEMPLAR_SWING.equals(id);
        if (slash) { LavaKatanaSlashAnimation.applyBodyPose(model, entity, t); return; }
        float weight = TEMPLAR_END.equals(id) ? 1 - t : Mth.clamp(t / (guard ? 0.075f : 0.15f), 0, 1)
                * (1 - Mth.clamp((t - (guard ? 41f / 42 : 0.4f)) / (guard ? 1f / 42 : 0.6f), 0, 1));
        float sign = entity.getMainArm() == HumanoidArm.RIGHT ? 1 : -1;
        var arm = sign > 0 ? model.rightArm : model.leftArm;
        var off = sign > 0 ? model.leftArm : model.rightArm;
        arm.xRot = Mth.lerp(weight, arm.xRot, -1.05f);
        arm.yRot = Mth.lerp(weight, arm.yRot, -0.23f * sign);
        arm.zRot = Mth.lerp(weight, arm.zRot, 0.08f * sign);
        off.xRot = Mth.lerp(weight * 0.65f, off.xRot, -0.75f);
        model.body.yRot += sign * weight * 0.06f;
    }
    private static WeaponSkillKeyframeTimeline.Keyframe key(float t, WeaponSkillPose p, WeaponSkillKeyframeTimeline.Easing e) {
        return new WeaponSkillKeyframeTimeline.Keyframe(t, p, e);
    }
}
