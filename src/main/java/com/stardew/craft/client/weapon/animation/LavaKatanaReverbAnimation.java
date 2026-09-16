package com.stardew.craft.client.weapon.animation;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Vector3f;
import static com.stardew.craft.client.weapon.animation.WeaponSkillKeyframeTimeline.Easing.*;

/** Keep the normal grip, lift slightly and press forward to ignite. */
public final class LavaKatanaReverbAnimation implements WeaponSkillAnimation {
    public static final LavaKatanaReverbAnimation INSTANCE = new LavaKatanaReverbAnimation();
    private static final WeaponSkillPose REST = new WeaponSkillPose(0, -90, 25, 1.13f, 3.2f, 1.13f);
    private static final WeaponSkillKeyframeTimeline TIMELINE = new WeaponSkillKeyframeTimeline(
            6, REST, new Vector3f(0, -0.375f, 0),
            new WeaponSkillKeyframeTimeline.Keyframe(0, REST, LINEAR),
            new WeaponSkillKeyframeTimeline.Keyframe(1.8f,
                    new WeaponSkillPose(-8, -90, 28, 1.3f, 4.0f, 1.5f), EASE_OUT_CUBIC),
            new WeaponSkillKeyframeTimeline.Keyframe(2.7f,
                    new WeaponSkillPose(4, -90, 23, 0.9f, 2.9f, -0.8f), EASE_IN_QUAD),
            new WeaponSkillKeyframeTimeline.Keyframe(6, REST, SMOOTH));

    @Override
    public boolean apply(PoseStack stack, HumanoidArm arm, float progress) {
        return TIMELINE.apply(stack, arm, progress);
    }

    static WeaponSkillPose sample(float progress) { return TIMELINE.sampleRight(progress); }

    static void applyBodyPose(HumanoidModel<?> model, LivingEntity entity, float t) {
        float weight = Mth.sin(Mth.clamp(t, 0, 1) * Mth.PI);
        float mirror = entity.getMainArm() == HumanoidArm.RIGHT ? 1 : -1;
        var main = mirror > 0 ? model.rightArm : model.leftArm;
        var off = mirror > 0 ? model.leftArm : model.rightArm;
        main.xRot = Mth.lerp(weight, main.xRot, -0.65f);
        main.yRot = Mth.lerp(weight, main.yRot, -0.10f * mirror);
        main.zRot = Mth.lerp(weight, main.zRot, 0.18f * mirror);
        off.xRot = Mth.lerp(weight * 0.25f, off.xRot, -0.35f);
        model.body.yRot += weight * 0.04f * mirror;
    }
}
