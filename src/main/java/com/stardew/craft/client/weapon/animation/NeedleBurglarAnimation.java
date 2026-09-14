package com.stardew.craft.client.weapon.animation;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Vector3f;
import static com.stardew.craft.client.weapon.MeleeWeaponVisuals.*;
import static com.stardew.craft.client.weapon.animation.WeaponSkillKeyframeTimeline.Easing.*;

/** Each needle stroke is clocked by its server attempt, never by a looping local timer. */
public final class NeedleBurglarAnimation implements WeaponSkillAnimation {
    private static final WeaponSkillPose REST = new WeaponSkillPose(0, -90, 25, 1.13f, 3.2f, 1.13f);
    private static final Vector3f GRIP = new Vector3f(0, -.375f, 0);
    private static final WeaponSkillKeyframeTimeline NORMAL = needle(8, false);
    private static final WeaponSkillKeyframeTimeline STRIKE = needle(3, false);
    private static final WeaponSkillKeyframeTimeline FINAL = needle(3, true);
    private static final WeaponSkillKeyframeTimeline CUT = drawCut(false);
    private static final WeaponSkillKeyframeTimeline STEAL = drawCut(true);
    private static final WeaponSkillKeyframeTimeline IGNITE = new WeaponSkillKeyframeTimeline(8, REST, GRIP,
            key(0, REST, LINEAR), key(1.3f, new WeaponSkillPose(-15, -83, 17, 1.4f, 4.1f, .25f), EASE_OUT_CUBIC),
            key(3, new WeaponSkillPose(-10, -86, 22, 1.2f, 3.8f, -.25f), SMOOTH), key(8, REST, SMOOTH));
    private final String id;
    public NeedleBurglarAnimation(String id) { this.id = id; }
    public static boolean supports(String id) {
        return NEEDLE_SWING.equals(id) || NEEDLE_READY.equals(id) || NEEDLE_STRIKE.equals(id)
                || NEEDLE_FINAL.equals(id) || NEEDLE_FRENZY.equals(id) || BURGLAR_SWING.equals(id) || BURGLAR_STRIKE.equals(id);
    }
    private static WeaponSkillKeyframeTimeline timeline(String id) {
        return switch (id) {
            case NEEDLE_SWING -> NORMAL;
            case NEEDLE_STRIKE -> STRIKE;
            case NEEDLE_FINAL -> FINAL;
            case NEEDLE_FRENZY -> IGNITE;
            case BURGLAR_SWING -> CUT;
            case BURGLAR_STRIKE -> STEAL;
            default -> throw new IllegalArgumentException(id);
        };
    }
    static WeaponSkillPose sample(String id, float t) { return NEEDLE_READY.equals(id) ? REST : timeline(id).sampleRight(t); }
    @Override public boolean apply(PoseStack pose, HumanoidArm arm, float t) {
        if (NEEDLE_READY.equals(id)) return true;
        return timeline(id).apply(pose, arm, t);
    }
    private static WeaponSkillKeyframeTimeline needle(float duration, boolean last) {
        return new WeaponSkillKeyframeTimeline(duration, REST, GRIP,
                key(0, REST, LINEAR), key(duration * .05f, new WeaponSkillPose(-27, -84, 16, 1.5f, 3.45f, .35f), EASE_OUT_CUBIC),
                key(duration * .185f, new WeaponSkillPose(-43, -86, 21, .5f, 3.05f, last ? -6.1f : -4.7f), EASE_IN_QUAD),
                key(duration * .36f, new WeaponSkillPose(-22, -84, 25, 1.0f, 3.25f, -1.4f), EASE_OUT_CUBIC),
                key(duration, REST, SMOOTH));
    }
    private static WeaponSkillKeyframeTimeline drawCut(boolean skill) {
        return new WeaponSkillKeyframeTimeline(8, REST, GRIP,
                key(0, REST, LINEAR), key(.4f, new WeaponSkillPose(-19, -74, 12, 2.1f, 3.6f, .7f), EASE_OUT_CUBIC),
                key(1.48f, new WeaponSkillPose(-38, -77, 29, .15f, 2.95f, skill ? -5.3f : -3.6f), EASE_IN_QUAD),
                key(2.8f, new WeaponSkillPose(-18, -61, 48, skill ? -1.8f : -.8f, 3.1f, -.65f), EASE_OUT_CUBIC),
                key(4.8f, new WeaponSkillPose(-6, -81, 32, .7f, 3.3f, .6f), SMOOTH), key(8, REST, SMOOTH));
    }
    static void applyBodyPose(HumanoidModel<?> model, LivingEntity entity, String id, float t) {
        if (NEEDLE_READY.equals(id)) return;
        float weight = Mth.clamp(t / .05f, 0, 1) * (1 - Mth.clamp((t - .3f) / .7f, 0, 1));
        float sign = entity.getMainArm() == HumanoidArm.RIGHT ? 1 : -1;
        boolean ignite = NEEDLE_FRENZY.equals(id), draw = BURGLAR_STRIKE.equals(id) || BURGLAR_SWING.equals(id);
        var arm = sign > 0 ? model.rightArm : model.leftArm;
        arm.xRot = Mth.lerp(weight, arm.xRot, ignite ? -.8f : -1.46f);
        arm.yRot = Mth.lerp(weight, arm.yRot, sign * (draw ? -.2f : -.04f));
        arm.zRot = Mth.lerp(weight, arm.zRot, sign * (draw ? .16f : .03f));
        if (!ignite) { arm.z -= weight * (NEEDLE_FINAL.equals(id) ? 2.2f : 1.65f); model.body.yRot += weight * sign * .075f; }
    }
    private static WeaponSkillKeyframeTimeline.Keyframe key(float t, WeaponSkillPose p, WeaponSkillKeyframeTimeline.Easing e) {
        return new WeaponSkillKeyframeTimeline.Keyframe(t, p, e);
    }
}
