package com.stardew.craft.client.weapon.animation;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Vector3f;
import static com.stardew.craft.client.weapon.MeleeWeaponVisuals.*;
import static com.stardew.craft.client.weapon.animation.WeaponSkillKeyframeTimeline.Easing.*;

public final class GalaxyWeaponAnimation implements WeaponSkillAnimation {
    private static final WeaponSkillPose REST = new WeaponSkillPose(0, -90, 25, 1.13f, 3.2f, 1.13f);
    private static final WeaponSkillPose READY = new WeaponSkillPose(-70, -85, 16, 1.7f, 2.8f, 0.1f);
    private static final Vector3f GRIP = new Vector3f(0, -0.375f, 0);
    private static final WeaponSkillKeyframeTimeline RIFT = new WeaponSkillKeyframeTimeline(8, REST, GRIP,
            key(0, REST, LINEAR), key(0.4f, new WeaponSkillPose(-35, -57, -8, 3.1f, 3.9f, 0.4f), EASE_OUT_CUBIC),
            key(1.48f, new WeaponSkillPose(-58, -32, 64, -3.4f, 2.0f, -5.7f), EASE_IN_QUAD),
            key(4, new WeaponSkillPose(-32, -40, 70, -2.4f, 1.8f, -2.3f), EASE_OUT_CUBIC), key(8, REST, SMOOTH));
    private static final WeaponSkillKeyframeTimeline JUDGEMENT = new WeaponSkillKeyframeTimeline(12, REST, GRIP,
            key(0, REST, LINEAR), key(0.65f, new WeaponSkillPose(-25, -80, 15, 2.3f, 4.7f, 0.5f), EASE_OUT_CUBIC),
            key(2.22f, new WeaponSkillPose(12, -70, 43, -0.2f, 0.6f, -4.8f), EASE_IN_QUAD),
            key(4, new WeaponSkillPose(12, -70, 43, -0.2f, 0.6f, -4.8f), LINEAR), key(12, REST, SMOOTH));
    private static final WeaponSkillKeyframeTimeline PREPARE = new WeaponSkillKeyframeTimeline(6, REST, GRIP,
            key(0, REST, LINEAR), key(0.4f, READY, EASE_OUT_CUBIC), key(2, READY, LINEAR), key(6, REST, SMOOTH));
    private static final WeaponSkillKeyframeTimeline STAB = new WeaponSkillKeyframeTimeline(4, REST, GRIP,
            key(0, READY, LINEAR), key(0.74f, new WeaponSkillPose(-86, -86, 17, 0.5f, 2.5f, -7.0f), EASE_IN_QUAD),
            key(1.6f, READY, EASE_OUT_CUBIC), key(2, READY, LINEAR), key(4, REST, SMOOTH));
    private static final WeaponSkillKeyframeTimeline LEAP = new WeaponSkillKeyframeTimeline(8, REST, GRIP,
            key(0, REST, LINEAR), key(0.35f, READY, EASE_OUT_CUBIC),
            key(1.48f, new WeaponSkillPose(-87, -86, 12, 0.2f, 2.4f, -8.7f), EASE_IN_QUAD),
            key(2.7f, new WeaponSkillPose(-74, -81, 29, 2.1f, 2.7f, -3.4f), EASE_OUT_CUBIC), key(8, REST, SMOOTH));
    private final String skill;
    public GalaxyWeaponAnimation(String skill) { this.skill = skill; }
    private static WeaponSkillKeyframeTimeline timeline(String id) {
        return switch (id) {
            case GALAXY_RIFT -> RIFT;
            case GALAXY_JUDGEMENT -> JUDGEMENT;
            case GALAXY_READY -> PREPARE;
            case GALAXY_STAB -> STAB;
            case GALAXY_LEAP -> LEAP;
            default -> throw new IllegalArgumentException("Not a galaxy action: " + id);
        };
    }
    @Override public boolean apply(PoseStack stack, HumanoidArm arm, float progress) { return timeline(skill).apply(stack, arm, progress); }
    static WeaponSkillPose sample(String id, float t) { return timeline(id).sampleRight(t); }
    static void applyBodyPose(HumanoidModel<?> model, LivingEntity entity, String id, float t) {
        if (GALAXY_RIFT.equals(id)) { LavaKatanaSlashAnimation.applyBodyPose(model, entity, t); return; }
        float weight = Mth.clamp(t / 0.06f, 0, 1) * (1 - Mth.clamp((t - 0.36f) / 0.64f, 0, 1));
        float sign = entity.getMainArm() == HumanoidArm.RIGHT ? 1 : -1;
        var arm = sign > 0 ? model.rightArm : model.leftArm;
        boolean slash = GALAXY_JUDGEMENT.equals(id), ready = GALAXY_READY.equals(id);
        arm.xRot = Mth.lerp(weight, arm.xRot, slash ? Mth.lerp(Mth.clamp(t / 0.185f, 0, 1), -1.4f, -0.55f) : ready ? -1.1f : -1.5f);
        arm.yRot = Mth.lerp(weight, arm.yRot, -0.1f * sign);
        arm.zRot = Mth.lerp(weight, arm.zRot, 0.07f * sign);
        if (slash) model.body.xRot += weight * 0.14f;
        else if (!ready) { arm.z -= weight * 2.4f; model.body.yRot += weight * sign * 0.07f; }
    }
    private static WeaponSkillKeyframeTimeline.Keyframe key(float t, WeaponSkillPose p, WeaponSkillKeyframeTimeline.Easing e) {
        return new WeaponSkillKeyframeTimeline.Keyframe(t, p, e);
    }
}
