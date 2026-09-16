package com.stardew.craft.client.weapon.animation;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Vector3f;
import static com.stardew.craft.client.weapon.MeleeWeaponVisuals.*;
import static com.stardew.craft.client.weapon.animation.WeaponSkillKeyframeTimeline.Easing.*;

/** Forest gathers beside the shoulder; elf attacks stay narrow and recover quickly. */
public final class GroveWeaponAnimation implements WeaponSkillAnimation {
    private static final WeaponSkillPose REST = new WeaponSkillPose(0, -90, 25, 1.13f, 3.2f, 1.13f);
    private static final WeaponSkillPose GATHER = new WeaponSkillPose(-24, -71, 13, 2.8f, 4.2f, 0.6f);
    private static final Vector3f GRIP = new Vector3f(0, -0.375f, 0);
    private static final WeaponSkillKeyframeTimeline READY = new WeaponSkillKeyframeTimeline(8, REST, GRIP,
            key(0, REST, LINEAR), key(2.4f, GATHER, EASE_OUT_CUBIC), key(3.5f, GATHER, LINEAR), key(8, REST, SMOOTH));
    private static final WeaponSkillKeyframeTimeline RELEASE = new WeaponSkillKeyframeTimeline(8, REST, GRIP,
            key(0, GATHER, LINEAR), key(1.48f, new WeaponSkillPose(-43, -47, 53, -2.3f, 1.9f, -5.1f), EASE_IN_QUAD),
            key(2.8f, new WeaponSkillPose(-24, -45, 66, -2.9f, 2.0f, -2.4f), EASE_OUT_CUBIC), key(8, REST, SMOOTH));
    private static final WeaponSkillKeyframeTimeline CAST = new WeaponSkillKeyframeTimeline(8, REST, GRIP,
            key(0, REST, LINEAR), key(1.2f, new WeaponSkillPose(-32, -76, 14, 1.8f, 3.9f, -0.4f), EASE_OUT_CUBIC),
            key(2.2f, new WeaponSkillPose(-40, -66, 30, 0.5f, 3.6f, -2.0f), SMOOTH), key(8, REST, SMOOTH));
    private static final WeaponSkillKeyframeTimeline STAB = new WeaponSkillKeyframeTimeline(6, REST, GRIP,
            key(0, REST, LINEAR), key(0.25f, new WeaponSkillPose(-70, -84, 16, 1.7f, 2.8f, 0.2f), EASE_OUT_CUBIC),
            key(1.11f, new WeaponSkillPose(-86, -87, 11, 0.5f, 2.6f, -6.9f), EASE_IN_QUAD),
            key(2.3f, new WeaponSkillPose(-63, -77, 20, 1.5f, 2.9f, -1.6f), EASE_OUT_CUBIC), key(6, REST, SMOOTH));
    private final String skill;
    public GroveWeaponAnimation(String skill) { this.skill = skill; }
    private static WeaponSkillKeyframeTimeline timeline(String id) {
        return switch (id) {
            case FOREST_READY -> READY;
            case FOREST_RELEASE -> RELEASE;
            case ELF_CAST -> CAST;
            case ELF_SWING -> STAB;
            default -> throw new IllegalArgumentException("Not a grove action: " + id);
        };
    }
    @Override public boolean apply(PoseStack stack, HumanoidArm hand, float t) { return timeline(skill).apply(stack, hand, t); }
    static WeaponSkillPose sample(String id, float t) { return timeline(id).sampleRight(t); }
    static void applyBodyPose(HumanoidModel<?> model, LivingEntity entity, String id, float t) {
        if (FOREST_RELEASE.equals(id)) { LavaKatanaSlashAnimation.applyBodyPose(model, entity, t); return; }
        float w = Mth.clamp(t / 0.09f, 0, 1) * (1 - Mth.clamp((t - 0.35f) / 0.65f, 0, 1));
        float sign = entity.getMainArm() == HumanoidArm.RIGHT ? 1 : -1;
        var arm = sign > 0 ? model.rightArm : model.leftArm;
        arm.xRot = Mth.lerp(w, arm.xRot, ELF_SWING.equals(id) ? -1.5f : -1.0f);
        arm.yRot = Mth.lerp(w, arm.yRot, -0.10f * sign);
        arm.zRot = Mth.lerp(w, arm.zRot, 0.06f * sign);
        if (ELF_SWING.equals(id)) { arm.z -= w * 2; model.body.yRot += sign * w * 0.06f; }
    }
    private static WeaponSkillKeyframeTimeline.Keyframe key(float t, WeaponSkillPose p, WeaponSkillKeyframeTimeline.Easing e) {
        return new WeaponSkillKeyframeTimeline.Keyframe(t, p, e);
    }
}
