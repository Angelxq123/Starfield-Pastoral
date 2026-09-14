package com.stardew.craft.mixin;

import com.stardew.craft.combat.WeaponMeleeProfile;
import com.stardew.craft.item.weapon.IStardewWeapon;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Uses the already-synced attack-speed projection for the normal swing's recovery pose. */
@Mixin(LivingEntity.class)
public abstract class LivingEntityWeaponSwingTimingMixin {
    @Inject(method = "getCurrentSwingDuration", at = @At("HEAD"), cancellable = true)
    private void stardewcraft$weaponSwingDuration(CallbackInfoReturnable<Integer> cir) {
        if ((Object) this instanceof Player player && player.swingingArm == InteractionHand.MAIN_HAND
                && player.getMainHandItem().getItem() instanceof IStardewWeapon weapon
                && WeaponMeleeProfile.get(weapon.getWeaponId()) != null
                && !WeaponMeleeProfile.isSample(weapon.getWeaponId())) {
            cir.setReturnValue(Math.max(1, Mth.ceil(player.getCurrentItemAttackStrengthDelay())));
        }
    }
}
