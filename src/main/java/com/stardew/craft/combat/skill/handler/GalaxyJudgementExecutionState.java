package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.combat.skill.SkillContext;
import com.stardew.craft.combat.skill.WeaponDamageSnapshot;
import com.stardew.craft.combat.skill.WeaponSkillDamage;
import com.stardew.craft.combat.skill.runtime.SkillExecutionContext;
import com.stardew.craft.combat.skill.runtime.SkillInstance;
import com.stardew.craft.combat.skill.runtime.SkillTickResult;
import java.util.List;
import java.util.Objects;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** One Galaxy Judgement cast and its three authored delayed starfalls. */
final class GalaxyJudgementExecutionState
        implements SkillInstance.ExecutionState {
    private long nextStrikeTick;
    private int remainingStrikes;
    private final int extraHits;
    private final double radius;
    private final float damageMultiplier;
    private final String skillId;
    private final ResourceKey<Level> dimension;
    private final WeaponDamageSnapshot weaponSnapshot;

    GalaxyJudgementExecutionState(
            long nowTick,
            int strikes,
            int extraHits,
            double radius,
            float damageMultiplier,
            String skillId,
            ResourceKey<Level> dimension,
            WeaponDamageSnapshot weaponSnapshot
    ) {
        if (strikes <= 0) {
            throw new IllegalArgumentException(
                    "Galaxy Judgement requires at least one starfall"
            );
        }
        this.nextStrikeTick = nextStrikeTick(nowTick);
        this.remainingStrikes = strikes;
        this.extraHits = Math.min(
                GalaxyJudgementSkillHandler.MAX_STARFALL_EXTRA_HITS,
                Math.max(0, extraHits)
        );
        this.radius = radius;
        this.damageMultiplier = damageMultiplier;
        this.skillId = Objects.requireNonNull(skillId, "skillId");
        this.dimension = Objects.requireNonNull(dimension, "dimension");
        this.weaponSnapshot = Objects.requireNonNull(
                weaponSnapshot,
                "weaponSnapshot"
        );
    }

    SkillTickResult advance(SkillExecutionContext context) {
        ServerPlayer player = context.player();
        if (!isValidContext(
                player.isAlive() && !player.isRemoved(),
                dimension.equals(player.level().dimension())
        )) {
            return SkillTickResult.CANCEL;
        }
        if (!isStrikeDue(context.nowTick())) {
            return SkillTickResult.CONTINUE;
        }

        strike(player, context.nowTick());
        return recordCompletedStrike(context.nowTick());
    }

    boolean isStrikeDue(long nowTick) {
        return nowTick >= nextStrikeTick;
    }

    SkillTickResult recordCompletedStrike(long actualNowTick) {
        remainingStrikes--;
        if (remainingStrikes <= 0) {
            return SkillTickResult.COMPLETE;
        }
        nextStrikeTick = nextStrikeTick(actualNowTick);
        return SkillTickResult.CONTINUE;
    }

    long nextStrikeTick() {
        return nextStrikeTick;
    }

    int remainingStrikes() {
        return remainingStrikes;
    }

    int hitsPerTarget() {
        return 1 + Math.max(0, extraHits);
    }

    @SuppressWarnings("null")
    private void strike(ServerPlayer player, long nowTick) {
        ServerLevel level = player.serverLevel();
        Vec3 center = player.position();
        AABB box = new AABB(
                center.x - radius,
                center.y - 1.5D,
                center.z - radius,
                center.x + radius,
                center.y + 2.0D,
                center.z + radius
        );
        List<LivingEntity> targets = List.copyOf(
                level.getEntitiesOfClass(
                        LivingEntity.class,
                        box,
                        entity -> entity.isPickable() && entity != player
                )
        );

        com.stardew.craft.combat.network.GalaxyPhasePayload.send(player, false, center, center);

        int hits = hitsPerTarget();
        for (LivingEntity target : targets) {
            for (int index = 0; index < hits; index++) {
                SkillContext hitContext = createStrikeContext(
                        skillId,
                        damageMultiplier
                );
                WeaponSkillDamage.apply(
                        player,
                        target,
                        hitContext,
                        weaponSnapshot,
                        nowTick + GalaxyJudgementSkillHandler
                                .HIT_CONTEXT_LIFETIME_TICKS,
                        WeaponSkillDamage.AttackGatePolicy
                                .SKILL_DAMAGE,
                        WeaponSkillDamage.HitCooldownPolicy
                                .BYPASS_FOR_AUTHORED_SEQUENCE
                );
            }
        }

    }

    static long nextStrikeTick(long actualNowTick) {
        return actualNowTick
                + GalaxyJudgementSkillHandler
                        .STARFALL_STRIKE_INTERVAL_TICKS;
    }

    static boolean isValidContext(
            boolean casterAvailable,
            boolean sameDimension
    ) {
        return casterAvailable && sameDimension;
    }

    static SkillContext createStrikeContext(
            String skillId,
            float damageMultiplier
    ) {
        return SkillContext.builder()
                .skillId(skillId)
                .tier(SkillContext.SkillTier.MAJOR)
                .damageMultiplier(damageMultiplier)
                .build();
    }
}
