package com.stardew.craft.integration.ae2;

import com.stardew.craft.StardewCraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;

/** Reject our blocks before AE2 asks them to construct collision geometry. */
public final class Ae2FacadeExclusions {
    private Ae2FacadeExclusions() {}

    public static boolean isExcluded(ItemStack stack) {
        return stack.getItem() instanceof BlockItem blockItem
                && StardewCraft.MODID.equals(BuiltInRegistries.BLOCK.getKey(blockItem.getBlock()).getNamespace());
    }
}
