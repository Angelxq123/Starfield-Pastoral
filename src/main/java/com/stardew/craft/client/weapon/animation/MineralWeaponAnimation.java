package com.stardew.craft.client.weapon.animation;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Vector3f;
import static com.stardew.craft.client.weapon.MeleeWeaponVisuals.*;
import static com.stardew.craft.client.weapon.animation.WeaponSkillKeyframeTimeline.Easing.*;

/** Obsidian draws a deliberate seam; bone uses compact scoring gestures with an upright wrist. */
public final class MineralWeaponAnimation implements WeaponSkillAnimation {
    private static final WeaponSkillPose REST = new WeaponSkillPose(0, -90, 25, 1.13f, 3.2f, 1.13f);
    private static final Vector3f GRIP = new Vector3f(0, -0.375f, 0);
    private static final WeaponSkillKeyframeTimeline OBSIDIAN = new WeaponSkillKeyframeTimeline(6, REST, GRIP,
            key(0, REST, LINEAR), key(0.3f, new WeaponSkillPose(-24, -65, 8, 2.4f, 4.0f, 0.4f), EASE_OUT_CUBIC),
            key(1.11f, new WeaponSkillPose(-32, -49, 54, -2.4f, 2.5f, -4.5f), EASE_IN_QUAD),
            key(2.1f, new WeaponSkillPose(-20, -50, 59, -2.7f, 2.5f, -2.5f), EASE_OUT_CUBIC), key(6, REST, SMOOTH));
    private static final WeaponSkillKeyframeTimeline BONE = new WeaponSkillKeyframeTimeline(6, REST, GRIP,
            key(0, REST, LINEAR), key(0.25f, new WeaponSkillPose(-27, -82, 10, 1.6f, 4.0f, 0.3f), EASE_OUT_CUBIC),
            key(1.11f, new WeaponSkillPose(-37, -76, 38, -0.7f, 2.1f, -4.0f), EASE_IN_QUAD),
            key(1.9f, new WeaponSkillPose(-22, -72, 41, -1.0f, 2.2f, -2.1f), EASE_OUT_CUBIC), key(6, REST, SMOOTH));
    private static final WeaponSkillKeyframeTimeline CRACK = new WeaponSkillKeyframeTimeline(12, REST, GRIP,
            key(0, REST, LINEAR), key(3, new WeaponSkillPose(-30, -61, 5, 2.6f, 4.4f, 0.3f), EASE_OUT_CUBIC),
            key(6, new WeaponSkillPose(-32, -59, 5, 2.7f, 4.3f, 0.1f), SMOOTH),
            key(8, new WeaponSkillPose(-36, -49, 58, -3.0f, 2.2f, -5.0f), EASE_IN_QUAD),
            key(8.7f, new WeaponSkillPose(-25, -50, 60, -3.1f, 2.3f, -3.8f), EASE_OUT_CUBIC), key(12, REST, SMOOTH));
    private static final WeaponSkillKeyframeTimeline MARK = new WeaponSkillKeyframeTimeline(8, REST, GRIP,
            key(0, REST, LINEAR), key(1, new WeaponSkillPose(-38, -78, 17, 0.8f, 3.5f, -2.4f), EASE_OUT_CUBIC),
            key(2.3f, new WeaponSkillPose(-28, -71, 34, 0.2f, 2.8f, -2.0f), SMOOTH), key(8, REST, SMOOTH));
    private static final WeaponSkillKeyframeTimeline EXECUTION = new WeaponSkillKeyframeTimeline(10, REST, GRIP,
            key(0, REST, LINEAR), key(0.8f, new WeaponSkillPose(-26, -78, 10, 1.3f, 4.2f, -0.5f), EASE_OUT_CUBIC),
            key(2, new WeaponSkillPose(-30, -73, 38, 0.1f, 2.0f, -3.3f), EASE_IN_QUAD), key(10, REST, SMOOTH));
    private final String skill;
    public MineralWeaponAnimation(String skill) { this.skill = skill; }
    public static boolean supports(String id) {
        return OBSIDIAN_SWING.equals(id) || OSSIFIED_SWING.equals(id) || OBSIDIAN_CRACK.equals(id)
                || OSSIFIED_MARK.equals(id) || OSSIFIED_EXECUTION.equals(id);
    }
    private static WeaponSkillKeyframeTimeline timeline(String id) {
        return switch (id) {
            case OBSIDIAN_SWING -> OBSIDIAN;
            case OSSIFIED_SWING -> BONE;
            case OBSIDIAN_CRACK -> CRACK;
            case OSSIFIED_MARK -> MARK;
            case OSSIFIED_EXECUTION -> EXECUTION;
            default -> throw new IllegalArgumentException(id);
        };
    }
    @Override public boolean apply(PoseStack pose, HumanoidArm hand, float t) { return timeline(skill).apply(pose, hand, t); }
    static WeaponSkillPose sample(String id, float t) { return timeline(id).sampleRight(t); }
    static void applyBodyPose(HumanoidModel<?> model, LivingEntity entity, String id, float t) {
        boolean crack = OBSIDIAN_CRACK.equals(id);
        if (OBSIDIAN_SWING.equals(id) || OSSIFIED_SWING.equals(id)) {
            LavaKatanaSlashAnimation.applyBodyPose(model, entity, t); return;
        }
        float weight = Mth.clamp(t / (crack ? 0.25f : 0.08f), 0, 1)
                * (1 - Mth.clamp((t - (crack ? 0.72f : 0.3f)) / (crack ? 0.28f : 0.7f), 0, 1));
        float release = crack ? Mth.clamp((t-0.5f)/(1f/6), 0, 1) : Mth.clamp(t/0.2f, 0, 1);
        float sign = entity.getMainArm() == HumanoidArm.RIGHT ? 1 : -1;
        var arm = sign > 0 ? model.rightArm : model.leftArm;
        arm.xRot = Mth.lerp(weight, arm.xRot, Mth.lerp(release, -1.5f, -0.65f));
        arm.yRot = Mth.lerp(weight, arm.yRot, sign*Mth.lerp(release, -0.2f, 0.3f));
        arm.zRot = Mth.lerp(weight, arm.zRot, sign*0.08f);
        model.body.yRot += sign*weight*Mth.lerp(release, -0.1f, 0.15f);
    }
    private static WeaponSkillKeyframeTimeline.Keyframe key(float t, WeaponSkillPose p, WeaponSkillKeyframeTimeline.Easing e) {
        return new WeaponSkillKeyframeTimeline.Keyframe(t, p, e);
    }
}
