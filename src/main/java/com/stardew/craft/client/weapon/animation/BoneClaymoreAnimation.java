package com.stardew.craft.client.weapon.animation;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Vector3f;
import static com.stardew.craft.client.weapon.MeleeWeaponVisuals.*;
import static com.stardew.craft.client.weapon.animation.WeaponSkillKeyframeTimeline.Easing.*;

/** Each stroke recovers independently: a missing return target must not strand the blade offscreen. */
public final class BoneClaymoreAnimation implements WeaponSkillAnimation {
    private static final WeaponSkillPose REST = new WeaponSkillPose(0, -90, 25, 1.13f, 3.2f, 1.13f);
    private static final Vector3f GRIP = new Vector3f(0, -.375f, 0);
    private static final WeaponSkillKeyframeTimeline BONE = cut(false, false), FRACTURE = cut(true, false);
    private static final WeaponSkillKeyframeTimeline CLAYMORE = cut(false, true), OUTWARD = sweep(false), RETURN = sweep(true);
    private final String id;
    public BoneClaymoreAnimation(String id) { this.id = id; }
    public static boolean supports(String id) {
        return BONE_SWORD_SWING.equals(id) || BONE_FRACTURE.equals(id) || CLAYMORE_SWING.equals(id)
                || CLAYMORE_OUT.equals(id) || CLAYMORE_RETURN.equals(id);
    }
    private static WeaponSkillKeyframeTimeline timeline(String id) {
        return switch (id) {
            case BONE_SWORD_SWING -> BONE;
            case BONE_FRACTURE -> FRACTURE;
            case CLAYMORE_SWING -> CLAYMORE;
            case CLAYMORE_OUT -> OUTWARD;
            case CLAYMORE_RETURN -> RETURN;
            default -> throw new IllegalArgumentException(id);
        };
    }
    static WeaponSkillPose sample(String id, float t) { return timeline(id).sampleRight(t); }
    @Override public boolean apply(PoseStack stack, HumanoidArm arm, float t) { return timeline(id).apply(stack, arm, t); }
    private static WeaponSkillKeyframeTimeline cut(boolean fracture, boolean broad) {
        return new WeaponSkillKeyframeTimeline(8, REST, GRIP,
                key(0, REST, LINEAR), key(.5f, new WeaponSkillPose(-27, -69, 8, 2.5f, 4.3f, .2f), EASE_OUT_CUBIC),
                key(1.48f, new WeaponSkillPose(-38, -68, 49, broad ? -2.3f : -1.4f, fracture ? 1.6f : 2.25f, fracture ? -4.5f : -3.5f), EASE_IN_QUAD),
                key(broad ? 3.5f : 2.5f, new WeaponSkillPose(-25, -75, 43, -1.1f, 2.4f, -1.6f), EASE_OUT_CUBIC),
                key(8, REST, SMOOTH));
    }
    private static WeaponSkillKeyframeTimeline sweep(boolean back) {
        return new WeaponSkillKeyframeTimeline(12, REST, GRIP,
                key(0, REST, LINEAR),
                key(.8f, new WeaponSkillPose(-26, back ? -78 : -64, back ? 46 : 4, back ? -1.3f : 3.1f, 3.9f, -.1f), EASE_OUT_CUBIC),
                key(2.22f, new WeaponSkillPose(-34, back ? -63 : -72, back ? 6 : 53, back ? 3.3f : -2.9f, back ? 3.2f : 2.1f, back ? -4.1f : -3.8f), EASE_IN_QUAD),
                key(4.5f, new WeaponSkillPose(-25, back ? -70 : -78, back ? 13 : 46, back ? 2.5f : -1.6f, 2.9f, -1.8f), EASE_OUT_CUBIC),
                key(6, new WeaponSkillPose(-22, back ? -73 : -80, back ? 16 : 43, back ? 2.1f : -1.3f, 3, -1.4f), SMOOTH),
                key(12, REST, SMOOTH));
    }
    static void applyBodyPose(HumanoidModel<?> model, LivingEntity entity, String id, float t) {
        boolean back = CLAYMORE_RETURN.equals(id), broad = CLAYMORE_SWING.equals(id) || CLAYMORE_OUT.equals(id) || back;
        float weight = Mth.clamp(t / .07f, 0, 1) * (1 - Mth.clamp((t - .36f) / .64f, 0, 1));
        float sweep = Mth.clamp((t - .07f) / .115f, 0, 1);
        float sign = entity.getMainArm() == HumanoidArm.RIGHT ? 1 : -1;
        var arm = sign > 0 ? model.rightArm : model.leftArm;
        arm.xRot = Mth.lerp(weight, arm.xRot, -1.15f - sweep * .18f);
        arm.yRot = Mth.lerp(weight, arm.yRot, sign * (back ? -1 : 1) * Mth.lerp(sweep, -.48f, .32f));
        arm.zRot = Mth.lerp(weight, arm.zRot, sign * .15f);
        model.body.yRot += weight * sign * (back ? -1 : 1) * (broad ? .18f : .1f) * (2 * sweep - 1);
    }
    private static WeaponSkillKeyframeTimeline.Keyframe key(float t, WeaponSkillPose p, WeaponSkillKeyframeTimeline.Easing e) {
        return new WeaponSkillKeyframeTimeline.Keyframe(t, p, e);
    }
}
