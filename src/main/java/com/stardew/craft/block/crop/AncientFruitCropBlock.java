package com.stardew.craft.block.crop;

import com.stardew.craft.item.ModItems;
import com.stardew.craft.item.quality.QualityHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.function.Supplier;

/**
 * 上古水果作物
 */
public class AncientFruitCropBlock extends TomatoCropBlock {

    private static final int[] PHASE_DAYS = new int[]{2, 7, 7, 7, 5}; // SDV: 28 days, five growth phases

    @Override
    protected Supplier<Item> getSeedsItem() {
        return ModItems.ANCIENT_FRUIT_SEEDS;
    }

    @Override
    protected Supplier<Item> getCropItem() {
        return ModItems.ANCIENT_FRUIT;
    }

    @Override
    protected boolean isInSeason(Level level) {
        if (level.isClientSide()) {
            return true;
        }
        return seasonForGrowth() == 0 || seasonForGrowth() == 1 || seasonForGrowth() == 2;
    }

    @Override
    protected int[] getPhaseDays() {
        return PHASE_DAYS;
    }

    @Override
    protected ItemStack getHarvestItem(int quality) {
        @SuppressWarnings("null")
        ItemStack stack = new ItemStack(ModItems.ANCIENT_FRUIT.get());
        QualityHelper.setQuality(stack, quality);
        return stack;
    }

    @Override
    protected boolean canRegrow() {
        return true;
    }

    @Override
    protected int getRegrowAge() {
        return 2;
    }

    @Override
    protected int getRegrowDays() {
        return 7;
    }

    @Override
    public String getCropDisplayNameKey() {
        return "item.stardewcraft.ancient_fruit";
    }
}
