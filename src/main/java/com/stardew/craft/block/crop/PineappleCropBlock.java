package com.stardew.craft.block.crop;

import com.stardew.craft.item.ModItems;
import com.stardew.craft.item.quality.QualityHelper;
import java.util.function.Supplier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Pineapple uses the two-block summer regrowth lifecycle with its own crop data. */
public class PineappleCropBlock extends TomatoCropBlock {
    private static final int[] PHASE_DAYS = {1, 3, 3, 4, 3};

    @Override
    protected Supplier<Item> getSeedsItem() {
        return ModItems.VANILLA_CATEGORY_ITEMS.get("pineapple_seeds");
    }

    @Override
    protected Supplier<Item> getCropItem() {
        return ModItems.VANILLA_CATEGORY_ITEMS.get("pineapple");
    }

    @Override
    protected int[] getPhaseDays() {
        return PHASE_DAYS;
    }

    @Override
    protected int getRegrowDays() {
        return 7;
    }

    @Override
    protected ItemStack getHarvestItem(int quality) {
        ItemStack stack = new ItemStack(getCropItem().get());
        QualityHelper.setQuality(stack, quality);
        return stack;
    }

    @Override
    public String getCropDisplayNameKey() {
        return "item.stardewcraft.pineapple";
    }
}
