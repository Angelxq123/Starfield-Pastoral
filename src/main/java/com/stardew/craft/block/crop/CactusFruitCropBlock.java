package com.stardew.craft.block.crop;

import com.stardew.craft.item.ModItems;
import com.stardew.craft.item.quality.QualityHelper;
import java.util.function.Supplier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Harvestable cactus; planting location is checked by CactusSeedItem. */
public class CactusFruitCropBlock extends TomatoCropBlock {
    private static final int[] PHASE_DAYS = {2, 2, 2, 3, 3};

    @Override
    protected Supplier<Item> getSeedsItem() {
        return ModItems.VANILLA_CATEGORY_ITEMS.get("cactus_seeds");
    }

    @Override
    protected Supplier<Item> getCropItem() {
        return ModItems.VANILLA_CATEGORY_ITEMS.get("cactus_fruit");
    }

    @Override
    protected int[] getPhaseDays() {
        return PHASE_DAYS;
    }

    @Override
    protected boolean isInSeason(Level level) {
        return true;
    }

    @Override
    protected int getRegrowDays() {
        return 3;
    }

    @Override
    protected ItemStack getHarvestItem(int quality) {
        ItemStack stack = new ItemStack(getCropItem().get());
        QualityHelper.setQuality(stack, quality);
        return stack;
    }

    @Override
    public String getCropDisplayNameKey() {
        return "item.stardewcraft.cactus_fruit";
    }
}
