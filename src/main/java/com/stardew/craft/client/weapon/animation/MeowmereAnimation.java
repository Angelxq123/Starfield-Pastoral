package com.stardew.craft.client.weapon.animation;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Vector3f;
import static com.stardew.craft.client.weapon.animation.WeaponSkillKeyframeTimeline.Easing.*;

/** Short aim, springy release and recovery; the fan shot makes one broader gesture. */
public final class MeowmereAnimation implements WeaponSkillAnimation {
    private static final WeaponSkillPose REST = new WeaponSkillPose(0, -90, 25, 1.13f, 3.2f, 1.13f);
    private static final Vector3f GRIP = new Vector3f(0, -0.375f, 0);
    private static final WeaponSkillKeyframeTimeline SHOT = new WeaponSkillKeyframeTimeline(10, REST, GRIP,
            key(0, REST, LINEAR), key(0.8f, new WeaponSkillPose(-42, -79, 16, 1.5f, 3.7f, -2.2f), EASE_OUT_CUBIC),
            key(2.0f, new WeaponSkillPose(-22, -82, 23, 1.8f, 3.7f, 0.2f), EASE_OUT_CUBIC), key(10, REST, SMOOTH));
    private static final WeaponSkillKeyframeTimeline FAN = new WeaponSkillKeyframeTimeline(15, REST, GRIP,
            key(0, REST, LINEAR), key(1.2f, new WeaponSkillPose(-35, -60, 32, -1.2f, 3.7f, -2.4f), EASE_OUT_CUBIC),
            key(3.0f, new WeaponSkillPose(-20, -69, 39, -1.0f, 3.9f, 0.3f), EASE_OUT_CUBIC), key(15, REST, SMOOTH));
    private final boolean major;
    public MeowmereAnimation(boolean major) { this.major = major; }
    @Override public boolean apply(PoseStack pose, HumanoidArm arm, float t) { return (major ? FAN : SHOT).apply(pose, arm, t); }
    static WeaponSkillPose sample(boolean major, float t) { return (major ? FAN : SHOT).sampleRight(t); }
    static void applyBodyPose(HumanoidModel<?> model, LivingEntity entity, float t) {
        float weight = Mth.clamp(t / 0.08f, 0, 1) * (1 - Mth.clamp((t - 0.2f) / 0.8f, 0, 1));
        float sign = entity.getMainArm() == HumanoidArm.RIGHT ? 1 : -1;
        var arm = sign > 0 ? model.rightArm : model.leftArm;
        arm.xRot = Mth.lerp(weight, arm.xRot, -1.3f);
        arm.yRot = Mth.lerp(weight, arm.yRot, -0.12f * sign);
        arm.zRot = Mth.lerp(weight, arm.zRot, 0.08f * sign);
        model.body.yRot += sign * weight * 0.07f;
    }
    private static WeaponSkillKeyframeTimeline.Keyframe key(float t, WeaponSkillPose p, WeaponSkillKeyframeTimeline.Easing e) {
        return new WeaponSkillKeyframeTimeline.Keyframe(t, p, e);
    }
}
