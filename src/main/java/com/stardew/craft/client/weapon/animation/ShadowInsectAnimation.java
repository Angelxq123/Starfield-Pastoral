package com.stardew.craft.client.weapon.animation;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Vector3f;
import static com.stardew.craft.client.weapon.MeleeWeaponVisuals.*;
import static com.stardew.craft.client.weapon.animation.WeaponSkillKeyframeTimeline.Easing.*;

/** A close draw-stab and a forward, mandible-like cut; neither flips the grip. */
public final class ShadowInsectAnimation implements WeaponSkillAnimation {
    private static final WeaponSkillPose REST = new WeaponSkillPose(0, -90, 25, 1.13f, 3.2f, 1.13f);
    private static final Vector3f GRIP = new Vector3f(0, -.375f, 0);
    private static final WeaponSkillKeyframeTimeline SHADOW = stab(false);
    private static final WeaponSkillKeyframeTimeline EXECUTE = stab(true);
    private static final WeaponSkillKeyframeTimeline CUT = cut(false);
    private static final WeaponSkillKeyframeTimeline DASH = cut(true);
    private static final WeaponSkillKeyframeTimeline STANCE = new WeaponSkillKeyframeTimeline(6, REST, GRIP,
            key(0, REST, LINEAR), key(1.3f, new WeaponSkillPose(-18, -82, 12, 1.6f, 4.15f, .2f), EASE_OUT_CUBIC),
            key(2.5f, new WeaponSkillPose(-12, -86, 19, 1.3f, 3.8f, -.2f), SMOOTH), key(6, REST, SMOOTH));
    private final String id;
    public ShadowInsectAnimation(String id) { this.id = id; }
    public static boolean supports(String id) {
        return SHADOW_SWING.equals(id) || SHADOW_EXECUTE.equals(id) || INSECT_SWING.equals(id)
                || INSECT_STANCE.equals(id) || INSECT_DASH.equals(id);
    }
    private static WeaponSkillKeyframeTimeline timeline(String id) {
        return switch (id) {
            case SHADOW_SWING -> SHADOW;
            case SHADOW_EXECUTE -> EXECUTE;
            case INSECT_SWING -> CUT;
            case INSECT_DASH -> DASH;
            case INSECT_STANCE -> STANCE;
            default -> throw new IllegalArgumentException(id);
        };
    }
    static WeaponSkillPose sample(String id, float t) { return timeline(id).sampleRight(t); }
    @Override public boolean apply(PoseStack pose, HumanoidArm arm, float t) { return timeline(id).apply(pose, arm, t); }
    private static WeaponSkillKeyframeTimeline stab(boolean execute) {
        return new WeaponSkillKeyframeTimeline(8, REST, GRIP,
                key(0, REST, LINEAR), key(.4f, new WeaponSkillPose(-25, -77, 14, 1.9f, 3.4f, .8f), EASE_OUT_CUBIC),
                key(1.48f, new WeaponSkillPose(-48, -82, 20, .35f, 3.0f, execute ? -6.3f : -4.6f), EASE_IN_QUAD),
                key(2.7f, new WeaponSkillPose(-22, -66, 39, execute ? -1.1f : -.2f, 3.15f, -1.6f), EASE_OUT_CUBIC),
                key(4.5f, new WeaponSkillPose(-8, -83, 32, .8f, 3.3f, .55f), SMOOTH), key(8, REST, SMOOTH));
    }
    private static WeaponSkillKeyframeTimeline cut(boolean dash) {
        return new WeaponSkillKeyframeTimeline(8, REST, GRIP,
                key(0, REST, LINEAR), key(.4f, new WeaponSkillPose(-20, -62, 5, 2.8f, 4.2f, .3f), EASE_OUT_CUBIC),
                key(1.48f, new WeaponSkillPose(-37, -73, 46, -1.2f, 2.5f, dash ? -5.3f : -3.5f), EASE_IN_QUAD),
                key(3.0f, new WeaponSkillPose(-17, -81, 41, -.3f, 2.9f, dash ? -2.7f : -.9f), EASE_OUT_CUBIC),
                key(8, REST, SMOOTH));
    }
    static void applyBodyPose(HumanoidModel<?> model, LivingEntity entity, String id, float t) {
        float weight = Mth.clamp(t / .06f, 0, 1) * (1 - Mth.clamp((t - .3f) / .7f, 0, 1));
        float sign = entity.getMainArm() == HumanoidArm.RIGHT ? 1 : -1;
        var arm = sign > 0 ? model.rightArm : model.leftArm;
        boolean stance = INSECT_STANCE.equals(id), insect = INSECT_SWING.equals(id) || INSECT_DASH.equals(id);
        arm.xRot = Mth.lerp(weight, arm.xRot, stance ? -.8f : -1.4f);
        arm.yRot = Mth.lerp(weight, arm.yRot, sign * (insect ? -.23f : -.07f));
        arm.zRot = Mth.lerp(weight, arm.zRot, sign * (insect ? .19f : .06f));
        if (!stance) { arm.z -= weight * 1.8f; model.body.yRot += weight * sign * (insect ? .15f : .07f); }
    }
    private static WeaponSkillKeyframeTimeline.Keyframe key(float t, WeaponSkillPose p, WeaponSkillKeyframeTimeline.Easing e) {
        return new WeaponSkillKeyframeTimeline.Keyframe(t, p, e);
    }
}
