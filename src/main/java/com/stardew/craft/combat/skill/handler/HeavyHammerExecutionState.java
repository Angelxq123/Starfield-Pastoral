package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.combat.ResolvedWeaponHit;
import com.stardew.craft.combat.StardewWeaponSpeedRules;
import com.stardew.craft.combat.WeaponStats;
import com.stardew.craft.combat.equipment.EquipmentResolver;
import com.stardew.craft.combat.equipment.EquipmentNegativeStatusProtection;
import com.stardew.craft.combat.network.HeavyHammerFxPayload;
import com.stardew.craft.combat.skill.*;
import com.stardew.craft.combat.skill.runtime.*;
import com.stardew.craft.event.FarmAreaProtectionEvents;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import static com.stardew.craft.combat.skill.handler.HeavyHammerRules.*;

final class HeavyHammerExecutionState implements SkillInstance.ExecutionState {
    final long startTick;
    private final String skill, weapon;
    private final int slot;
    private final ItemStack heldStack;
    private final Vec3 origin, forward;
    private final Set<UUID> swept = new HashSet<>();
    private final Set<UUID> pullResisted = new HashSet<>(), pullChecked = new HashSet<>();
    private Vec3 center;
    private int phase;
    private boolean canceled;
    private final HeavyHammerPoundCadence cadence;
    private long poundContactTick = Long.MIN_VALUE;

    HeavyHammerExecutionState(SkillExecutionContext c, String skill) {
        startTick = c.nowTick(); this.skill = skill; weapon = c.weaponId().getPath();
        slot = c.player().getInventory().selected;
        heldStack = c.player().getMainHandItem();
        origin = c.player().position(); forward = forward(c.player());
        cadence = new HeavyHammerPoundCadence(startTick);
    }
    boolean validHeld(ServerPlayer p) {
        return !canceled && p.isAlive() && !p.isSpectator() && p.getInventory().selected == slot
                && p.getMainHandItem() == heldStack;
    }
    boolean buffActive(long now) { return !canceled && ENDLESS.equals(skill) && now >= startTick + BURST_START && now < startTick + BURST_START + BURST_DURATION; }
    void attackIntent(long now, boolean held) { cadence.input(now, held); }
    void cancel() { canceled = true; }
    void sendBuff(ServerPlayer p, boolean on) {
        HeavyHammerFxPayload.send(p, skill, on ? HeavyHammerFxPayload.BUFF_START : HeavyHammerFxPayload.BUFF_END,
                p.position(), on ? BURST_START + BURST_DURATION : 0, -1);
    }
    SkillTickResult advance(SkillExecutionContext c) {
        if (!validHeld(c.player())) return SkillTickResult.CANCEL;
        long age = c.nowTick() - startTick;
        if (ENDLESS.equals(skill)) {
            if (age >= BURST_START + BURST_DURATION) return SkillTickResult.COMPLETE;
            boolean blocked = WeaponSkillAnimationLock.isLocked(c.player(), c.nowTick())
                    || YetiFreezeTracker.isMovementLocked(c.player(), c.nowTick())
                    || c.player().containerMenu != c.player().inventoryMenu;
            if (blocked) poundContactTick = Long.MIN_VALUE;
            if (poundContactTick != Long.MIN_VALUE && c.nowTick() >= poundContactTick) {
                poundContactTick = Long.MIN_VALUE;
                pound(c);
                if (!validHeld(c.player())) return SkillTickResult.CANCEL;
            }
            double interval = attackInterval(c.player());
            if (buffActive(c.nowTick()) && cadence.advance(c.nowTick(), interval, blocked)) {
                int duration = poundAnimationTicks(interval);
                poundContactTick = c.nowTick() + poundContactTicks(duration);
                WeaponSkillAnimationDispatcher.sendSkillAnim(c.player(), weapon, POUND, duration);
            }
            return SkillTickResult.CONTINUE;
        }
        if (SWEEP.equals(skill)) {
            if (phase == 0 && age >= 5) {
                breakCaches(c, origin, 3.5, point -> arc(point, 3.5, 80));
                for (LivingEntity target : targets(c, origin, 3.5)) if (arc(target, 3.5, 80)) {
                    swept.add(target.getUUID()); damage(c, target, skill, 2f);
                }
                HeavyHammerFxPayload.send(c.player(), skill, HeavyHammerFxPayload.SWEEP, origin, 5, -1,
                        (float)Math.toDegrees(Math.atan2(-forward.x, forward.z)));
                phase++;
            }
            if (phase == 1 && age >= 9) {
                breakCaches(c, origin, 5, point -> horizontalDistanceSqr(point, origin) > 3.5 * 3.5 && arc(point, 5, 80));
                for (LivingEntity target : targets(c, origin, 5)) if (!swept.contains(target.getUUID())
                        && horizontalDistanceSqr(target.position(), origin) > 3.5 * 3.5 && arc(target, 5, 80))
                    damage(c, target, skill + "_outer", .8f);
                phase++;
            }
            return age >= 12 ? SkillTickResult.COMPLETE : SkillTickResult.CONTINUE;
        }
        if (QUAKE.equals(skill)) {
            if (phase < 3 && age >= quakeTick(phase)) {
                if (phase == 0) center = ground(c.player(), c.player().position().add(forward.scale(.85)));
                if (center != null) {
                    String hitId = phase == 2 ? skill + "_final" : phase == 1 ? skill + "_echo" : skill;
                    breakCaches(c, center, 4.5, point -> true);
                    for (LivingEntity target : targets(c, center, 4.5)) damage(c, target, hitId, quakeDamage(phase));
                    HeavyHammerFxPayload.send(c.player(), hitId, phase == 2 ? HeavyHammerFxPayload.FINAL : HeavyHammerFxPayload.QUAKE,
                            center, 4.5f, -1);
                }
                phase++;
            }
            return phase >= 3 ? SkillTickResult.COMPLETE : SkillTickResult.CONTINUE;
        }
        if (PRESS.equals(skill)) {
            if (center == null) center = ground(c.player(), origin.add(forward.scale(1.5)));
            if (age <= 5 && center != null) pull(c);
            if (phase == 0 && age >= 8) {
                if (center != null) {
                    breakCaches(c, center, 3, point -> true);
                    for (LivingEntity target : targets(c, center, 3)) damage(c, target, skill, 2.4f);
                    HeavyHammerFxPayload.send(c.player(), skill, HeavyHammerFxPayload.PRESS, center, 3, -1);
                }
                phase++;
            }
            if (phase == 1 && age >= 13) {
                if (center != null) {
                    breakCaches(c, center, 3, point -> true);
                    for (LivingEntity target : targets(c, center, 3)) damage(c, target, skill + "_echo", .6f);
                    HeavyHammerFxPayload.send(c.player(), skill, HeavyHammerFxPayload.ECHO, center, 3, -1);
                }
                phase++;
            }
            return age >= 15 ? SkillTickResult.COMPLETE : SkillTickResult.CONTINUE;
        }
        return SkillTickResult.CANCEL;
    }
    private void pound(SkillExecutionContext c) {
        Vec3 point = ground(c.player(), c.player().position().add(forward(c.player()).scale(1.5)));
        if (point == null) return;
        breakCaches(c, point, 3, candidate -> true);
        for (LivingEntity target : targets(c, point, 3)) {
            if (!validHeld(c.player())) return;
            damage(c, target, POUND, 1f);
        }
        HeavyHammerFxPayload.send(c.player(), POUND, HeavyHammerFxPayload.POUND, point, 3, -1);
    }
    private void pull(SkillExecutionContext c) {
        for (LivingEntity target : targets(c, origin, 4)) {
            if (!arc(target, 4, 90) || target.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) >= .8) continue;
            if (pullChecked.add(target.getUUID()) && EquipmentNegativeStatusProtection.decide(target, 5).resisted())
                pullResisted.add(target.getUUID());
            if (pullResisted.contains(target.getUUID())) continue;
            Vec3 delta = center.subtract(target.position()).multiply(1, 0, 1);
            if (delta.lengthSqr() < .09) continue;
            Vec3 step = delta.normalize().scale(Math.min(.35, delta.length()));
            // Pull through velocity so vanilla travel and collision resolve the movement once.
            Vec3 current = target.getDeltaMovement();
            target.setDeltaMovement(current.x + (step.x-current.x)*.65, current.y,
                    current.z + (step.z-current.z)*.65);
            target.hasImpulse = true;
            if (target instanceof ServerPlayer other) {
                WeaponSkillMovementArbiter.revokeCurrent(other);
                other.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(other));
            }

        }
    }
    private boolean arc(LivingEntity target, double range, double angle) {
        return arc(target.position(), range, angle);
    }
    private boolean arc(Vec3 point, double range, double angle) {
        Vec3 offset = point.subtract(origin);
        return HeavyHammerRules.inArc(offset.dot(forward), offset.dot(new Vec3(-forward.z, 0, forward.x)), range, angle);
    }
    private static void breakCaches(SkillExecutionContext c, Vec3 center, double radius, java.util.function.Predicate<Vec3> shape) {
        com.stardew.craft.event.MineBarrelBreakHandler.breakInVolume(c.player(),
                new AABB(center.add(-radius,-1.5,-radius),center.add(radius,2.5,radius)), center.add(0,.65,0),
                point -> horizontalDistanceSqr(point,center)<=radius*radius && shape.test(point));
    }
    static java.util.List<LivingEntity> targets(SkillExecutionContext c, Vec3 center, double radius) {
        return c.player().serverLevel().getEntitiesOfClass(LivingEntity.class,
                new AABB(center.add(-radius, -1.5, -radius), center.add(radius, 2.5, radius)),
                t -> candidate(c.player(), t) && horizontalDistanceSqr(t.position(), center) <= radius * radius
                        && visible(c.player(), center.add(0, .65, 0), t));
    }
    private static boolean candidate(ServerPlayer player, LivingEntity target) {
        if (target == player || !target.isAlive() || !target.isAttackable() || target.isSpectator()
                || player.isAlliedTo(target) || target.isInvulnerableTo(player.damageSources().playerAttack(player))) return false;
        if (target instanceof Player other && (!player.canHarmPlayer(other) || other.isCreative())) return false;
        return player.isCreative() || player.level().dimension() != com.stardew.craft.core.ModDimensions.STARDEW_VALLEY
                || !FarmAreaProtectionEvents.isOnProtectedFarm(player, target.blockPosition());
    }
    private static boolean visible(ServerPlayer player, Vec3 center, LivingEntity target) {
        return player.hasLineOfSight(target) && player.level().clip(new ClipContext(center, target.getBoundingBox().getCenter(),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player)).getType() == HitResult.Type.MISS;
    }
    static double horizontalDistanceSqr(Vec3 a, Vec3 b) { double x = a.x - b.x, z = a.z - b.z; return x*x + z*z; }
    static Vec3 forward(Player player) {
        Vec3 look = player.getLookAngle().multiply(1, 0, 1);
        return look.lengthSqr() > 1.0E-8 ? look.normalize() : Vec3.directionFromRotation(0, player.getYRot());
    }
    static Vec3 ground(ServerPlayer player, Vec3 position) {
        var hit = WeaponGroundContact.find(player.level(), player, position);
        return hit == null ? null : hit.getLocation();
    }
    private static double attackInterval(ServerPlayer p) {
        WeaponStats stats = WeaponStats.fromItemStack(p.getMainHandItem());
        return burstInterval(StardewWeaponSpeedRules.repeatMillisecondsFromRawSpeed(stats.getWeaponType(), stats.getRawSpeed(),
                EquipmentResolver.getMergedStats(p).getWeaponSpeedMultiplier() + stats.getWeaponSpeedMultiplier()) / 50);
    }
    private static void damage(SkillExecutionContext c, LivingEntity target, String id, float multiplier) {
        WeaponSkillDamage.apply(c.player(), target, SkillContext.builder().skillId(id)
                .tier(QUAKE.equals(c.skillData().getId()) || ENDLESS.equals(c.skillData().getId()) ? SkillContext.SkillTier.MAJOR : SkillContext.SkillTier.MINOR)
                .damageMultiplier(multiplier).defaultKnockback(false).build(), c.weaponSnapshot(), c.nowTick() + 2,
                WeaponSkillDamage.AttackGatePolicy.RESPECT_AT_IMPACT, WeaponSkillDamage.HitCooldownPolicy.BYPASS_FOR_AUTHORED_SEQUENCE);
    }
    static void appliedHit(ServerPlayer player, ResolvedWeaponHit hit) {
        String id = hit.authoredSkillContext().getSkillId();
        if (!(id.startsWith(SWEEP) || id.startsWith(QUAKE) || id.startsWith(PRESS) || POUND.equals(id))) return;
        boolean echo = id.endsWith("_echo") || id.endsWith("_outer");
        int stagger = id.equals(SWEEP) ? 4 : id.equals(QUAKE) ? 4 : id.equals(QUAKE + "_echo") ? 3
                : id.equals(QUAKE + "_final") ? 6 : id.equals(PRESS) ? 5 : id.equals(POUND) ? 2 : 0;
        LivingEntity target = hit.target();
        long now = hit.gameTick();
        if (stagger > 0 && target.isAlive() && target.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) < .8
                && now >= target.getPersistentData().getLong("stardewcraft_hammer_stagger_ready")
                && !YetiFreezeTracker.isMovementLocked(target, now)) {
            int applied = YetiFreezeTracker.applyWithEquipmentProtection(target, now, stagger, YetiFreezeTracker.PresentationPolicy.SERVER_ONLY_STAGGER);
            if (applied > 0) {
                target.getPersistentData().putLong("stardewcraft_hammer_stagger_ready", now + applied + 2);
                float push = id.equals(QUAKE + "_final") ? .5f : id.equals(SWEEP) ? .25f : id.equals(POUND) ? .12f : 0;
                if (push > 0) com.stardew.craft.combat.HeavyHammerCombatEvents.queuePush(
                        target, player.position(), push, now + applied);
            }
        }
        Vec3 point = target.getBoundingBox().clip(player.getEyePosition(), target.getBoundingBox().getCenter()).orElse(target.getBoundingBox().getCenter());
        HeavyHammerFxPayload.send(player, id, echo ? HeavyHammerFxPayload.HIT_ECHO : HeavyHammerFxPayload.HIT,
                point, id.endsWith("_final") ? 1.25f : POUND.equals(id) ? .75f : 1f, target.getId());
    }
}
