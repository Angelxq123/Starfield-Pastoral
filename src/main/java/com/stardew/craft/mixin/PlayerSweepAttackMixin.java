package com.stardew.craft.mixin;

import com.stardew.craft.combat.OrdinaryWeaponAttackFrameStore;
import com.stardew.craft.combat.WeaponCombatIdentity;
import com.stardew.craft.combat.WeaponCombatDebug;
import com.stardew.craft.combat.WeaponCombatEvents;
import com.stardew.craft.combat.skill.WeaponDamageSnapshot;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.entity.PartEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Player.class)
public abstract class PlayerSweepAttackMixin {
    private static final float NATIVE_DAMAGE_SENTINEL = 1.0F;

    @Redirect(
            method = "attack",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Entity;hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z"
            )
    )
    private boolean stardewcraft$authorizedPrimaryHurt(
            Entity target,
            DamageSource source,
            float vanillaDamage
    ) {
        Player player = (Player) (Object) this;
        ItemStack weapon = player.getMainHandItem();
        WeaponCombatIdentity.Resolved identity = WeaponCombatIdentity
                .resolve(weapon)
                .orElse(null);
        LivingEntity combatTarget = canonicalLivingTarget(target);
        if (identity == null || combatTarget == null) {
            return target.hurt(source, vanillaDamage);
        }

        WeaponDamageSnapshot snapshot = WeaponDamageSnapshot.capture(
                identity.id(),
                weapon
        );
        OrdinaryWeaponAttackFrameStore.bind(
                player,
                combatTarget,
                source,
                snapshot,
                player.level().getGameTime()
        );
        float stardewDamageInput = stableStardewDamageInput(vanillaDamage);
        float healthBefore = combatTarget.getHealth();
        WeaponCombatDebug.log(
                "native_hurt_begin",
                player,
                combatTarget,
                "vanillaInput={} stardewInput={} invulnerableTime={} lastHurt={} bypassesCooldown={} targetHealth={}",
                vanillaDamage,
                stardewDamageInput,
                combatTarget.invulnerableTime,
                WeaponCombatDebug.lastHurt(combatTarget),
                source.is(DamageTypeTags.BYPASSES_COOLDOWN),
                WeaponCombatDebug.health(combatTarget)
        );
        try {
            boolean accepted = target.hurt(
                    source,
                    stardewDamageInput
            );
            if (!accepted) {
                WeaponCombatDebug.log(
                        "native_hurt_rejected",
                        player,
                        combatTarget,
                        "bukkit={} targetHealth={}",
                        WeaponCombatDebug.bukkitRejection(combatTarget),
                        WeaponCombatDebug.health(combatTarget)
                );
                WeaponCombatEvents.discardRejectedNativeHit(player);
            }
            WeaponCombatDebug.log(
                "native_hurt_end",
                player,
                combatTarget,
                    "accepted={} healthBefore={} healthAfter={} invulnerableTime={} lastHurt={} alive={}",
                    accepted,
                    healthBefore,
                    combatTarget.getHealth(),
                    combatTarget.invulnerableTime,
                    WeaponCombatDebug.lastHurt(combatTarget),
                    combatTarget.isAlive()
            );
            return accepted;
        } finally {
            OrdinaryWeaponAttackFrameStore.discard(
                    player,
                    combatTarget,
                    source
            );
        }
    }

    @Redirect(
            method = "attack",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z"
            )
    )
    private boolean stardewcraft$suppressNativeSweepHurt(
            LivingEntity target,
            DamageSource source,
            float vanillaDamage
    ) {
        Player player = (Player) (Object) this;
        if (WeaponCombatIdentity.isWeapon(player.getMainHandItem())) {
            return false;
        }
        return target.hurt(source, vanillaDamage);
    }

    private float stableStardewDamageInput(float vanillaDamage) {
        Player player = (Player) (Object) this;
        ItemStack weapon = player.getMainHandItem();
        if (!WeaponCombatIdentity.isWeapon(weapon)) {
            return vanillaDamage;
        }
        // Keep Player.attack's native damage as the Bukkit/Youer probe. A
        // one-point probe can be reduced to zero by armor or a server plugin
        // before LivingDamageEvent.Pre is reached. The Stardew amount is
        // restored in WeaponCombatEvents after that native protection pass.
        return Float.isFinite(vanillaDamage)
                ? Math.max(NATIVE_DAMAGE_SENTINEL, vanillaDamage)
                : NATIVE_DAMAGE_SENTINEL;
    }

    private static LivingEntity canonicalLivingTarget(Entity target) {
        Entity canonical = target instanceof PartEntity<?> part
                ? part.getParent()
                : target;
        return canonical instanceof LivingEntity living ? living : null;
    }

    /**
     * Stardew weapon hits apply their authored weight exactly once inside
     * WeaponCombatEvents. Suppress both vanilla's post-hurt primary knockback
     * and its pre-hurt sweep knockback while preserving vanilla weapons.
     */
    @Redirect(
            method = "attack",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;knockback(DDD)V"
            )
    )
    private void stardewcraft$singleKnockbackOwner(
            LivingEntity target,
            double strength,
            double x,
            double z
    ) {
        Player player = (Player) (Object) this;
        if (!WeaponCombatIdentity.isWeapon(player.getMainHandItem())) {
            target.knockback(strength, x, z);
        }
    }

}
