package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.combat.CombatHealing;
import com.stardew.craft.combat.skill.DashMovementTracker;
import com.stardew.craft.combat.skill.SkillContext;
import com.stardew.craft.combat.skill.StartrailTracker;
import com.stardew.craft.combat.skill.WeaponSkillAnimationDispatcher;
import com.stardew.craft.combat.skill.WeaponSkillAnimationLock;
import com.stardew.craft.combat.skill.WeaponSkillDamage;
import com.stardew.craft.combat.skill.WeaponSkillCooldowns;
import com.stardew.craft.combat.skill.runtime.RuntimeWeaponSkillHandler;
import com.stardew.craft.combat.skill.runtime.SkillExecutionContext;
import com.stardew.craft.combat.skill.runtime.SkillInstance;
import com.stardew.craft.combat.skill.runtime.SkillValidation;
import com.stardew.craft.combat.skill.runtime.WeaponSkillRuntime;
import com.stardew.craft.combat.skill.runtime.WeaponSkillMovementControl;
import com.stardew.craft.item.weapon.WeaponSkillData;
import com.stardew.craft.player.PlayerStardewDataAPI;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Server-authoritative extraction of Galaxy Sword's original Startrail Rift.
 */
public final class StartrailRiftSkillHandler
        implements RuntimeWeaponSkillHandler {
    public static final double DASH_DISTANCE = 4.5D;
    public static final double PATH_HIT_RADIUS = 0.9D;
    public static final int DASH_DURATION_TICKS = 5;
    public static final int BOOST_STACKS = 6;
    public static final float BOOST_CRITICAL_CHANCE = 0.20F;
    public static final int HIT_STARTRAIL_RESTORE = 2;
    public static final float HIT_ENERGY_RESTORE = 6.0F;
    public static final float HIT_HEALTH_RESTORE = 3.0F;
    public static final int SPEED_DURATION_TICKS = 140;
    public static final int SPEED_AMPLIFIER = 0;
    public static final int HIT_CONTEXT_LIFETIME_TICKS = 5;
    public static final int ANIMATION_TICKS = 8;

    @Override
    public SkillValidation validate(SkillExecutionContext context) {
        if (WeaponSkillMovementControl.isLocked(
                context.player(),
                context.nowTick()
        )) {
            return SkillValidation.reject(
                    SkillValidation.RejectionReason.INVALID_STATE
            );
        }
        if (WeaponSkillRuntime.hasActive(
                context.player().getUUID(),
                context.skillId()
        )) {
            return SkillValidation.reject(
                    SkillValidation.RejectionReason.INVALID_STATE
            );
        }
        if (WeaponSkillCooldowns.isOnCooldown(
                context.player(),
                context.weaponId().getPath(),
                context.skillData().getId(),
                context.nowTick()
        )) {
            return SkillValidation.reject(
                    SkillValidation.RejectionReason.COOLDOWN
            );
        }
        return DragonBreathThrustSkillHandler.resolveSafeDashEnd(
                context.player(),
                DASH_DISTANCE
        ) != null
                ? SkillValidation.accept()
                : SkillValidation.reject(
                        SkillValidation.RejectionReason.INVALID_STATE
                );
    }

    @Override
    public void begin(
            SkillExecutionContext context,
            SkillInstance instance
    ) {
        if (WeaponSkillMovementControl.isLocked(
                context.player(),
                context.nowTick()
        )) {
            throw new IllegalStateException(
                    "Validated Startrail Rift movement is now locked"
            );
        }
        Vec3 start = context.player().position();
        Vec3 end = DragonBreathThrustSkillHandler.resolveSafeDashEnd(
                context.player(),
                DASH_DISTANCE
        );
        if (end == null) {
            throw new IllegalStateException(
                    "Validated Startrail Rift path is no longer safe"
            );
        }

        List<LivingEntity> targets =
                DragonBreathThrustSkillHandler.findTargetsAlongPath(
                        context.player(),
                        start,
                        end,
                        PATH_HIT_RADIUS
                );
        instance.setTargetEntityIds(
                targets.stream().map(LivingEntity::getId).toList()
        );

        int stacks = StartrailTracker.getStacks(context.player());
        boolean boosted = isBoostedForStacks(stacks);
        String weaponId = context.weaponId().getPath();
        String skillId = context.skillData().getId();
        WeaponSkillRuntime.commitCooldown(
                context,
                instance,
                context.skillData().getCooldown() * 20
        );
        StartrailRiftExecutionState executionState =
                new StartrailRiftExecutionState(
                        targets.stream()
                                .map(LivingEntity::getUUID)
                                .toList()
                );
        instance.initializeExecutionState(executionState);

        instance.registerCommittedEffect(() -> {
            WeaponSkillAnimationDispatcher.sendSkillAnim(
                    context.player(),
                    weaponId,
                    skillId,
                    ANIMATION_TICKS
            );
            for (LivingEntity target : targets) {
                attackTarget(context, target, boosted);
            }
            DashMovementTracker.start(
                    context.player(),
                    context.nowTick(),
                    end,
                    DASH_DURATION_TICKS
            );
            context.player().addEffect(new MobEffectInstance(
                    MobEffects.MOVEMENT_SPEED,
                    SPEED_DURATION_TICKS,
                    SPEED_AMPLIFIER,
                    false,
                    true,
                    true
            ));

            WeaponSkillAnimationLock.setLock(
                    context.player(),
                    context.nowTick(),
                    ANIMATION_TICKS
            );
        });
    }

    /** Settles this cast's reward from its first exact positive applied hit. */
    public static boolean settleAppliedHitRewards(
            ServerPlayer player,
            LivingEntity target
    ) {
        if (player == null || target == null) {
            return false;
        }
        boolean claimed = WeaponSkillRuntime.activeExecutionState(
                player.getUUID(),
                BuiltinWeaponSkillHandlers.STARTRAIL_RIFT,
                StartrailRiftExecutionState.class
        ).map(state -> state.claimAppliedHitRewards(target.getUUID()))
                .orElse(false);
        if (!claimed) {
            return false;
        }
        StartrailTracker.addStacks(player, HIT_STARTRAIL_RESTORE);
        PlayerStardewDataAPI.restoreEnergy(player, HIT_ENERGY_RESTORE);
        CombatHealing.heal(player, HIT_HEALTH_RESTORE);
        return true;
    }

    static boolean isBoostedForStacks(int stacks) {
        return stacks >= BOOST_STACKS;
    }

    static float criticalChanceBonusForStacks(int stacks) {
        return isBoostedForStacks(stacks)
                ? BOOST_CRITICAL_CHANCE
                : 0.0F;
    }

    static SkillContext createHitContext(
            WeaponSkillData skillData,
            boolean boosted
    ) {
        return SkillContext.builder()
                .skillId(skillData.getId())
                .tier(SkillContext.SkillTier.MINOR)
                .damageMultiplier(
                        skillData.getDamagePercent() / 100.0F
                )
                .critChanceBonus(
                        boosted ? BOOST_CRITICAL_CHANCE : 0.0F
                )
                .build();
    }

    private static void attackTarget(
            SkillExecutionContext context,
            LivingEntity target,
            boolean boosted
    ) {
        WeaponSkillDamage.apply(
                context.player(),
                target,
                createHitContext(context.skillData(), boosted),
                context.weaponSnapshot(),
                context.nowTick() + HIT_CONTEXT_LIFETIME_TICKS,
                WeaponSkillDamage.AttackGatePolicy.RESPECT_AT_IMPACT,
                WeaponSkillDamage.HitCooldownPolicy.RESPECT_VANILLA
        );
    }

}
