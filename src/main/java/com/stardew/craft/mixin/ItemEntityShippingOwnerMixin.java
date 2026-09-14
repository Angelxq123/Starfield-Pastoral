package com.stardew.craft.mixin;

import com.stardew.craft.blockentity.ShippingBinBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Objects;
import java.util.UUID;

/** A merged entity retains only one thrower, so shippable drops must preserve that identity. */
@Mixin(ItemEntity.class)
public abstract class ItemEntityShippingOwnerMixin {
    @Shadow private UUID thrower;

    @Inject(method = "tryToMerge", at = @At("HEAD"), cancellable = true)
    private void stardewcraft$keepDepositorsSeparate(ItemEntity other, CallbackInfo ci) {
        if (!Objects.equals(thrower, ((ItemEntityShippingOwnerMixin) (Object) other).thrower)
                && ShippingBinBlockEntity.canShip(((ItemEntity) (Object) this).getItem())) {
            ci.cancel();
        }
    }
}
