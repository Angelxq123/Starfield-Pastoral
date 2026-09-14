package com.stardew.craft.client;

import com.stardew.craft.item.tool.FishingRodItem;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

/**
 * 客户端第一人称鱼竿动画状态机：
 * - 蓄力：base -> p5，保持 p5 并轻微抖动
 * - 甩杆：p5 -> p6（带一点过冲）
 * - 收杆：p6 -> base
 *
 * 纯视觉，不影响服务端钓鱼逻辑。
 */
public final class FishingRodCastAnimationState {
	public enum Phase {
		IDLE,
		CHARGING,
		CAST_OUT,
		CAST_HELD,
		REEL_IN
	}

	// Visual-only timing. Increase cast-out a bit so it doesn't look "too fast".
	public static final int CAST_OUT_TOTAL_TICKS = 10;
	public static final int REEL_IN_TOTAL_TICKS = 8;

	private static final HandState MAIN = new HandState();
	private static final HandState OFF = new HandState();

	private FishingRodCastAnimationState() {
	}

	public static void reset() {
		MAIN.reset();
		OFF.reset();
	}

	public static void tick() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null) {
			MAIN.reset();
			OFF.reset();
			return;
		}

		MAIN.tick(mc, InteractionHand.MAIN_HAND);
		OFF.tick(mc, InteractionHand.OFF_HAND);
	}

	public static Phase getPhase(InteractionHand hand, boolean isUsingThisHand, boolean castActive) {
		HandState s = state(hand);
		if (isUsingThisHand) {
			return Phase.CHARGING;
		}
		if (s.castOutTicksRemaining > 0) {
			return Phase.CAST_OUT;
		}
		if (s.reelInTicksRemaining > 0) {
			return Phase.REEL_IN;
		}
		if (castActive) {
			return Phase.CAST_HELD;
		}
		return Phase.IDLE;
	}

	public static float getCastOutProgress01(InteractionHand hand, float partialTick) {
		HandState s = state(hand);
		if (s.castOutTicksRemaining <= 0) {
			return 0.0F;
		}
		float elapsed = (CAST_OUT_TOTAL_TICKS - s.castOutTicksRemaining) + partialTick;
		return clamp01(elapsed / (float) CAST_OUT_TOTAL_TICKS);
	}

	public static float getReelInProgress01(InteractionHand hand, float partialTick) {
		HandState s = state(hand);
		if (s.reelInTicksRemaining <= 0) {
			return 0.0F;
		}
		float elapsed = (REEL_IN_TOTAL_TICKS - s.reelInTicksRemaining) + partialTick;
		return clamp01(elapsed / (float) REEL_IN_TOTAL_TICKS);
	}

	private static float clamp01(float v) {
		if (v < 0.0F) return 0.0F;
		if (v > 1.0F) return 1.0F;
		return v;
	}

	private static HandState state(InteractionHand hand) {
		return hand == InteractionHand.OFF_HAND ? OFF : MAIN;
	}

	private static final class HandState {
		private boolean prevCastActive = false;
		private int castOutTicksRemaining = 0;
		private int reelInTicksRemaining = 0;

		void reset() {
			prevCastActive = false;
			castOutTicksRemaining = 0;
			reelInTicksRemaining = 0;
		}

		@SuppressWarnings("null")
		void tick(Minecraft mc, InteractionHand hand) {
			if (castOutTicksRemaining > 0) {
				castOutTicksRemaining--;
			}
			if (reelInTicksRemaining > 0) {
				reelInTicksRemaining--;
			}

			var player = mc.player;
			if (player == null) {
				reset();
				return;
			}

			ItemStack stack = player.getItemInHand(hand);
			boolean isRod = stack.getItem() instanceof FishingRodItem;
			boolean castActive = isRod && FishingRodItem.isCastActive(stack);

			if (castActive && !prevCastActive) {
				castOutTicksRemaining = CAST_OUT_TOTAL_TICKS;
				reelInTicksRemaining = 0;
			} else if (!castActive && prevCastActive) {
				reelInTicksRemaining = REEL_IN_TOTAL_TICKS;
				castOutTicksRemaining = 0;
			}

			prevCastActive = castActive;

			// 蓄力时不让“收杆回位”抢镜：直接取消 reel-in 计时
			boolean isUsingThisHand = player.isUsingItem() && player.getUsedItemHand() == hand;
			if (isUsingThisHand) {
				reelInTicksRemaining = 0;
			}
		}
	}
}
