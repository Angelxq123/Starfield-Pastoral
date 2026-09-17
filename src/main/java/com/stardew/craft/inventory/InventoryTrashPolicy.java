package com.stardew.craft.inventory;

import com.stardew.craft.api.v1.item.StardewItemDataApi;
import com.stardew.craft.core.ModTags;
import com.stardew.craft.item.tool.FishingRodItem;
import com.stardew.craft.item.tool.PanItem;
import com.stardew.craft.item.weapon.SlingshotItem;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;

/** Shared client/server rule for the Stardew inventory trash can. */
public final class InventoryTrashPolicy {
    private InventoryTrashPolicy() {
    }

    public static boolean canTrash(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        if ("stardewcraft.type.quest".equals(StardewItemDataApi.getTypeKey(stack))) {
            return false;
        }
        if (stack.is(ModTags.Items.PREVENT_TRASHING)) {
            return false;
        }
        if (stack.getItem() instanceof FishingRodItem
                || stack.getItem() instanceof PanItem
                || stack.getItem() instanceof SlingshotItem) {
            return true;
        }
        if ("stardewcraft.type.tool".equals(StardewItemDataApi.getTypeKey(stack))) {
            return false;
        }
        return !stack.is(ItemTags.AXES)
                && !stack.is(ModTags.Items.PICKAXES)
                && !stack.is(ModTags.Items.HOES)
                && !stack.is(ModTags.Items.WATERING_CANS)
                && !stack.is(ModTags.Items.SCYTHES);
    }
}
