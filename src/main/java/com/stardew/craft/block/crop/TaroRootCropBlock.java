package com.stardew.craft.block.crop;

import com.stardew.craft.item.ModItems;
import com.stardew.craft.item.quality.QualityHelper;
import java.util.function.Supplier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Taro shares rice's two-block paddy lifecycle, with its own source crop data. */
public class TaroRootCropBlock extends RiceCropBlock {
    private static final int[] PHASE_DAYS = {1, 2, 3, 4};

    @Override
    protected Supplier<Item> getSeedsItem() {
        return ModItems.VANILLA_CATEGORY_ITEMS.get("taro_tuber");
    }

    @Override
    protected Supplier<Item> getCropItem() {
        return ModItems.VANILLA_CATEGORY_ITEMS.get("taro_root");
    }

    @Override
    protected boolean isInSeason(Level level) {
        return level.isClientSide() || seasonForGrowth() == 1;
    }

    @Override
    protected int[] getPhaseDays() {
        return PHASE_DAYS;
    }

    @Override
    protected ItemStack getHarvestItem(int quality) {
        ItemStack stack = new ItemStack(getCropItem().get());
        QualityHelper.setQuality(stack, quality);
        return stack;
    }

    @Override
    protected HarvestMethod getHarvestMethod() {
        return HarvestMethod.GRAB;
    }

    @Override
    protected float getHarvestMaxIncreasePerFarmingLevel() {
        return 0f;
    }

    @Override
    protected double getExtraHarvestChance() {
        return 0;
    }

    @Override
    public String getCropDisplayNameKey() {
        return "item.stardewcraft.taro_root";
    }
}
