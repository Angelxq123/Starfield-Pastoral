package com.stardew.craft.mixin;

import com.stardew.craft.integration.ae2.Ae2FacadeExclusions;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "appeng.items.parts.FacadeItem", remap = false)
public abstract class Ae2FacadeItemMixin {
    @Inject(method = "createFacadeForItem(Lnet/minecraft/world/item/ItemStack;Z)Lnet/minecraft/world/item/ItemStack;",
            at = @At("HEAD"), cancellable = true)
    private void stardewcraft$excludeBlocks(ItemStack stack, boolean returnItem,
                                          CallbackInfoReturnable<ItemStack> callback) {
        if (Ae2FacadeExclusions.isExcluded(stack)) callback.setReturnValue(ItemStack.EMPTY);
    }

    @Inject(method = "createFacadeForItemUnchecked(Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/world/item/ItemStack;",
            at = @At("HEAD"), cancellable = true)
    private void stardewcraft$excludeUncheckedBlocks(ItemStack stack, CallbackInfoReturnable<ItemStack> callback) {
        if (Ae2FacadeExclusions.isExcluded(stack)) callback.setReturnValue(ItemStack.EMPTY);
    }
}
