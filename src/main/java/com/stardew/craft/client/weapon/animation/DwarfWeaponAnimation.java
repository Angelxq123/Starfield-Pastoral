package com.stardew.craft.client.weapon.animation;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Vector3f;
import static com.stardew.craft.client.weapon.MeleeWeaponVisuals.*;
import static com.stardew.craft.client.weapon.animation.WeaponSkillKeyframeTimeline.Easing.*;

/** Squared, planted sword cuts and a point-first dagger. The wrist never rolls over. */
public final class DwarfWeaponAnimation implements WeaponSkillAnimation {
    private static final WeaponSkillPose REST = new WeaponSkillPose(0, -90, 25, 1.13f, 3.2f, 1.13f);
    private static final Vector3f GRIP = new Vector3f(0, -.375f, 0);
    private static final WeaponSkillKeyframeTimeline CUT = slash(false), GUARD = slash(true);
    private static final WeaponSkillKeyframeTimeline STAB = thrust(false), THRUST = thrust(true);
    private static final WeaponSkillKeyframeTimeline FORTRESS = new WeaponSkillKeyframeTimeline(10, REST, GRIP,
            key(0, REST, LINEAR), key(.7f, new WeaponSkillPose(-26, -75, 7, 1.3f, 4.3f, .2f), EASE_OUT_CUBIC),
            key(1.85f, new WeaponSkillPose(-14, -71, 35, -.1f, 2.15f, -2.1f), EASE_IN_QUAD),
            key(3.2f, new WeaponSkillPose(-14, -71, 35, -.1f, 2.15f, -2.1f), LINEAR), key(10, REST, SMOOTH));
    private static final WeaponSkillKeyframeTimeline RUSH = new WeaponSkillKeyframeTimeline(8, REST, GRIP,
            key(0, REST, LINEAR), key(1.3f, new WeaponSkillPose(-22, -80, 14, 1.6f, 3.95f, -.4f), EASE_OUT_CUBIC),
            key(3, new WeaponSkillPose(-16, -85, 22, 1.15f, 3.65f, -.6f), SMOOTH), key(8, REST, SMOOTH));
    private final String id;
    public DwarfWeaponAnimation(String id) { this.id = id; }
    public static boolean supports(String id) {
        return DWARF_SWORD_SWING.equals(id) || DWARF_GUARD.equals(id) || DWARF_FORTRESS.equals(id)
                || DWARF_DAGGER_SWING.equals(id) || DWARF_THRUST.equals(id) || DWARF_RUSH.equals(id);
    }
    private static WeaponSkillKeyframeTimeline timeline(String id) {
        return switch (id) {
            case DWARF_SWORD_SWING -> CUT;
            case DWARF_GUARD -> GUARD;
            case DWARF_FORTRESS -> FORTRESS;
            case DWARF_DAGGER_SWING -> STAB;
            case DWARF_THRUST -> THRUST;
            case DWARF_RUSH -> RUSH;
            default -> throw new IllegalArgumentException(id);
        };
    }
    static WeaponSkillPose sample(String id, float t) { return timeline(id).sampleRight(t); }
    @Override public boolean apply(PoseStack pose, HumanoidArm arm, float t) { return timeline(id).apply(pose, arm, t); }
    private static WeaponSkillKeyframeTimeline slash(boolean guard) {
        return new WeaponSkillKeyframeTimeline(8, REST, GRIP,
                key(0, REST, LINEAR), key(.5f, new WeaponSkillPose(-29, -62, 4, 2.8f, 4.25f, .4f), EASE_OUT_CUBIC),
                key(1.48f, new WeaponSkillPose(-31, -66, 51, guard ? -2.3f : -1.4f, 2.35f, guard ? -4.3f : -3.1f), EASE_IN_QUAD),
                key(2.8f, new WeaponSkillPose(-24, -72, 47, -.65f, 2.8f, -1.7f), EASE_OUT_CUBIC), key(8, REST, SMOOTH));
    }
    private static WeaponSkillKeyframeTimeline thrust(boolean skill) {
        return new WeaponSkillKeyframeTimeline(8, REST, GRIP,
                key(0, REST, LINEAR), key(.5f, new WeaponSkillPose(-28, -83, 12, 1.6f, 3.65f, .35f), EASE_OUT_CUBIC),
                key(1.48f, new WeaponSkillPose(-45, -84, 21, .35f, 3, skill ? -5.8f : -4.1f), EASE_IN_QUAD),
                key(skill ? 4.5f : 2.6f, new WeaponSkillPose(-30, -84, 23, .8f, 3.2f, skill ? -3.3f : -1.3f), EASE_OUT_CUBIC), key(8, REST, SMOOTH));
    }
    static void applyBodyPose(HumanoidModel<?> model, LivingEntity entity, String id, float t) {
        boolean dagger = DWARF_THRUST.equals(id) || DWARF_DAGGER_SWING.equals(id), fortress = DWARF_FORTRESS.equals(id);
        float weight = Mth.clamp(t / .06f, 0, 1) * (1 - Mth.clamp((t - .38f) / .62f, 0, 1));
        float sign = entity.getMainArm() == HumanoidArm.RIGHT ? 1 : -1;
        var arm = sign > 0 ? model.rightArm : model.leftArm;
        arm.xRot = Mth.lerp(weight, arm.xRot, dagger ? -1.45f : fortress ? -.9f : -1.15f);
        arm.yRot = Mth.lerp(weight, arm.yRot, sign * (dagger ? -.05f : -.24f));
        arm.zRot = Mth.lerp(weight, arm.zRot, sign * (dagger ? .05f : .18f));
        if (dagger) arm.z -= weight * 1.8f;
        else model.body.yRot += weight * sign * .12f;
    }
    private static WeaponSkillKeyframeTimeline.Keyframe key(float t, WeaponSkillPose p, WeaponSkillKeyframeTimeline.Easing e) {
        return new WeaponSkillKeyframeTimeline.Keyframe(t, p, e);
    }
}
