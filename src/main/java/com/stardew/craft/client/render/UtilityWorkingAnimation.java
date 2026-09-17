package com.stardew.craft.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;

/**
 * 实用设施通用工作态动画（参考数据包：熔炉/小桶弹跳式拉伸）
 * 允许个别设施使用独立动画时绕过此方法。
 */
public final class UtilityWorkingAnimation {
	private UtilityWorkingAnimation() {
	}

	public static void applyDefaultWorkingPose(PoseStack poseStack, Level level, BlockPos pos, float partialTick) {
		if (level == null) {
			return;
		}

		float time = getCycleTime(level, pos, partialTick);
		applyDefaultWorkingPoseByTime(poseStack, time);
	}

	public static void applyDefaultWorkingPoseByTicks(PoseStack poseStack, float animationTicks) {
		float mod = animationTicks % 30.0f;
		float time = mod < 0 ? mod + 30.0f : mod;
		applyDefaultWorkingPoseByTime(poseStack, time);
	}

	private static void applyDefaultWorkingPoseByTime(PoseStack poseStack, float time) {

		Keyframe k0 = new Keyframe(0f, 1.05f, 1.45f, 1.05f, -0.10f);
		Keyframe k1 = new Keyframe(8f, 1.35f, 1.05f, 1.35f, 0.05f);
		Keyframe k2 = new Keyframe(15f, 1.20f, 1.20f, 1.20f, 0.00f);
		Keyframe k3 = new Keyframe(23f, 1.25f, 1.15f, 1.25f, 0.02f);
		Keyframe k4 = new Keyframe(30f, 1.05f, 1.45f, 1.05f, -0.10f);

		Keyframe a;
		Keyframe b;
		if (time < k1.t) {
			a = k0;
			b = k1;
		} else if (time < k2.t) {
			a = k1;
			b = k2;
		} else if (time < k3.t) {
			a = k2;
			b = k3;
		} else {
			a = k3;
			b = k4;
		}

		float t = (time - a.t) / (b.t - a.t);
		float sx = Mth.lerp(t, a.sx, b.sx);
		float sy = Mth.lerp(t, a.sy, b.sy);
		float sz = Mth.lerp(t, a.sz, b.sz);
		float y = Mth.lerp(t, a.y, b.y);

		poseStack.translate(0.0f, y, 0.0f);
		poseStack.translate(0.5f, 0.5f, 0.5f);
		poseStack.scale(sx, sy, sz);
		poseStack.translate(-0.5f, -0.5f, -0.5f);
	}

	public static void applyKegWorkingPose(PoseStack poseStack, Level level, BlockPos pos, float partialTick) {
		if (level == null) {
			return;
		}

		float time = getCycleTime(level, pos, partialTick);
		applyKegWorkingPoseByTime(poseStack, time);
	}

	public static void applyKegWorkingPoseByTicks(PoseStack poseStack, float animationTicks) {
		float mod = animationTicks % 30.0f;
		float time = mod < 0 ? mod + 30.0f : mod;
		applyKegWorkingPoseByTime(poseStack, time);
	}

	private static void applyKegWorkingPoseByTime(PoseStack poseStack, float time) {

		Keyframe k0 = new Keyframe(0f, 1.05f, 1.45f, 1.05f, -0.10f);
		Keyframe k1 = new Keyframe(8f, 1.35f, 1.05f, 1.35f, 0.05f);
		Keyframe k2 = new Keyframe(15f, 1.20f, 1.20f, 1.20f, 0.00f);
		Keyframe k3 = new Keyframe(23f, 1.25f, 1.15f, 1.25f, 0.02f);
		Keyframe k4 = new Keyframe(30f, 1.05f, 1.45f, 1.05f, -0.10f);

		Keyframe a;
		Keyframe b;
		if (time < k1.t) {
			a = k0;
			b = k1;
		} else if (time < k2.t) {
			a = k1;
			b = k2;
		} else if (time < k3.t) {
			a = k2;
			b = k3;
		} else {
			a = k3;
			b = k4;
		}

		float t = (time - a.t) / (b.t - a.t);
		float sx = Mth.lerp(t, a.sx, b.sx);
		float sy = Mth.lerp(t, a.sy, b.sy);
		float sz = Mth.lerp(t, a.sz, b.sz);
		float y = Mth.lerp(t, a.y, b.y);

		float baseScale = 1.2f;
		sx /= baseScale;
		sy /= baseScale;
		sz /= baseScale;

		float scaleAmp = 0.55f;
		sx = 1.0f + (sx - 1.0f) * scaleAmp;
		sy = 1.0f + (sy - 1.0f) * scaleAmp;
		sz = 1.0f + (sz - 1.0f) * scaleAmp;
		sx = Mth.clamp(sx, 0.95f, 1.05f);
		sy = Mth.clamp(sy, 0.95f, 1.05f);
		sz = Mth.clamp(sz, 0.95f, 1.05f);
		y = Mth.clamp(y * 0.30f, 0.0f, 0.04f);

		poseStack.translate(0.0f, y, 0.0f);
		poseStack.translate(0.5f, 0.5f, 0.5f);
		poseStack.scale(sx, sy, sz);
		poseStack.translate(-0.5f, -0.5f, -0.5f);
	}

    /** Small source-style pulse around the base, so the authored machine never floats. */
    public static void applyGroundedWorkingPose(PoseStack poseStack, Level level, BlockPos pos, float partialTick) {
        if (level == null) return;
        applyGroundedWorkingPoseByTicks(poseStack, (level.getGameTime() % 1000) + partialTick);
    }

    public static void applyGroundedWorkingPoseByTicks(PoseStack poseStack, float ticks) {
        float width = 1 + (com.stardew.craft.model.OilMakerAnimation.widthScale(ticks) - 1) * 0.16F;
        float height = 1 + (com.stardew.craft.model.OilMakerAnimation.heightScale(ticks) - 1) * 0.16F;
        poseStack.translate(0.5, 0, 0.5);
        poseStack.scale(width, height, width);
        poseStack.translate(-0.5, 0, -0.5);
    }

	private static float getCycleTime(Level level, BlockPos pos, float partialTick) {
		long seed = pos.asLong();
		float phase = ((seed * 0x9E3779B97F4A7C15L) >>> 40) / 4096.0f;
		float t = level.getGameTime() + partialTick + phase * 30.0f;
		float mod = t % 30.0f;
		return mod < 0 ? mod + 30.0f : mod;
	}

	private record Keyframe(float t, float sx, float sy, float sz, float y) {}
}
