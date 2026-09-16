package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.combat.WeaponStats;
import com.stardew.craft.combat.skill.LavaKatanaMarkTracker;
import com.stardew.craft.combat.skill.SkillContext;
import com.stardew.craft.combat.skill.WeaponSkillDamage;
import com.stardew.craft.combat.skill.runtime.SkillExecutionContext;
import com.stardew.craft.combat.skill.runtime.SkillInstance;
import com.stardew.craft.combat.skill.runtime.SkillTickResult;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

/** One Molten Reverb window and its completion-only finisher. */
final class LavaKatanaReverbExecutionState
        implements SkillInstance.ExecutionState {
    static final String FINISHER_SKILL_ID = "lava_katana_finisher";
    static final int HIT_CONTEXT_LIFETIME_TICKS = 5;
    static final float FINISHER_BASE_SCALAR = 1.5F;
    static final float FINISHER_HEAT_SCALAR = 0.05F;

    private final ResourceKey<Level> dimension;
    private final long startTick;
    private final long endTick;
    private boolean settled;
    private boolean advancing;

    LavaKatanaReverbExecutionState(
        ResourceKey<Level> dimension,
        long nowTick,
        int durationTicks
    ) {
        if (durationTicks <= 0) {
            throw new IllegalArgumentException(
                "Molten Reverb duration must be positive"
            );
        }
        this.dimension = Objects.requireNonNull(dimension, "dimension");
        this.startTick = nowTick;
        this.endTick = nowTick + durationTicks;
    }

    long startTick() { return startTick; }

    boolean isActive(
        long nowTick,
        ResourceKey<Level> currentDimension
    ) {
        return !settled
            && dimension.equals(currentDimension)
            && isWithinActiveWindow(nowTick, endTick);
    }

    SkillTickResult advance(SkillExecutionContext context) {
        if (settled) {
            return SkillTickResult.COMPLETE;
        }
        if (advancing) {
            return SkillTickResult.CONTINUE;
        }
        if (!dimension.equals(context.player().level().dimension())) {
            cancel();
            return SkillTickResult.CANCEL;
        }
        if (isWithinActiveWindow(context.nowTick(), endTick)) {
            return SkillTickResult.CONTINUE;
        }

        advancing = true;
        try {
            finish(context);
            return SkillTickResult.COMPLETE;
        } finally {
            settled = true;
            advancing = false;
        }
    }

    void cancel() {
        settled = true;
    }

    private static boolean isWithinActiveWindow(
        long nowTick,
        long endTick
    ) {
        return nowTick <= endTick;
    }

    @SuppressWarnings("null")
    private static void finish(SkillExecutionContext context) {
        if (!(context.player().level() instanceof ServerLevel level)) {
            return;
        }
        Set<UUID> marked = LavaKatanaMarkTracker.getMarkedTargets(
            context.player().getUUID()
        );
        if (marked.isEmpty()) {
            return;
        }

        for (UUID targetId : marked) {
            if (targetId == null) {
                continue;
            }
            Entity entity = level.getEntity(targetId);
            if (!(entity instanceof LivingEntity target)
                || !target.isAlive()
                || !LavaKatanaMarkTracker.isMarkedBy(
                    target,
                    context.player(),
                    context.nowTick()
                )) {
                continue;
            }

            int remainingTicks = LavaKatanaMarkTracker.getRemainingTicks(
                target,
                context.nowTick()
            );
            if (remainingBurnJumps(remainingTicks) <= 0) {
                LavaKatanaMarkTracker.clearMark(target);
                continue;
            }

            int heat = LavaKatanaMarkTracker.getHeat(target);
            float damageMultiplier = finisherDamageMultiplier(
                remainingTicks,
                heat
            );
            WeaponStats weaponStats = WeaponStats.fromItemStack(
                context.weaponSnapshot().weapon()
            );
            if (weaponStats.getAverageDamage() <= 0.0F) {
                LavaKatanaMarkTracker.clearMark(target);
                continue;
            }

            WeaponSkillDamage.apply(
                context.player(),
                target,
                createFinisherContext(damageMultiplier),
                context.weaponSnapshot(),
                context.nowTick() + HIT_CONTEXT_LIFETIME_TICKS,
                WeaponSkillDamage.AttackGatePolicy.SKILL_DAMAGE,
                WeaponSkillDamage.HitCooldownPolicy
                        .BYPASS_FOR_AUTHORED_SEQUENCE
            );
            LavaKatanaMarkTracker.clearMark(target);
        }
    }

    private static int remainingBurnJumps(int remainingTicks) {
        return (int) Math.max(
            0L,
            ((long) remainingTicks
                + LavaKatanaMarkTracker.BURN_INTERVAL_TICKS
                - 1L)
                / LavaKatanaMarkTracker.BURN_INTERVAL_TICKS
        );
    }

    static float finisherDamageMultiplier(
        int remainingTicks,
        int heat
    ) {
        int nonNegativeHeat = Math.max(0, heat);
        float baseJumpRatio = LavaKatanaMarkTracker.BASE_BURN_RATIO
            + nonNegativeHeat
            * LavaKatanaMarkTracker.HEAT_BONUS_REVERB_RATIO;
        return remainingBurnJumps(remainingTicks)
            * baseJumpRatio
            * (FINISHER_BASE_SCALAR
                + FINISHER_HEAT_SCALAR * nonNegativeHeat);
    }

    static SkillContext createFinisherContext(float damageMultiplier) {
        return SkillContext.builder()
            .skillId(FINISHER_SKILL_ID)
            .tier(SkillContext.SkillTier.MAJOR)
            .damageMultiplier(damageMultiplier)
            .build();
    }

}
