package com.stardew.craft.block.crop;

import com.stardew.craft.item.ModItems;
import com.stardew.craft.item.quality.QualityHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.function.Supplier;

/**
 * 玉米作物
 */
public class CornCropBlock extends TomatoCropBlock {

    private static final int[] PHASE_DAYS = new int[]{2, 3, 3, 3, 3}; // SDV: 14 days

    @SuppressWarnings("null")
    public CornCropBlock() {
        super();
    }

    @Override
    protected Supplier<Item> getSeedsItem() {
        return ModItems.CORN_SEEDS;
    }

    @Override
    protected Supplier<Item> getCropItem() {
        return ModItems.CORN;
    }

    @Override
    protected boolean isInSeason(Level level) {
        if (level.isClientSide()) {
            return true;
        }
        return seasonForGrowth() == 1 || seasonForGrowth() == 2;
    }

    @Override
    protected int[] getPhaseDays() {
        return PHASE_DAYS;
    }

    @Override
    protected ItemStack getHarvestItem(int quality) {
        @SuppressWarnings("null")
        ItemStack stack = new ItemStack(ModItems.CORN.get());
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
        return 4;
    }

    @Override
    public String getCropDisplayNameKey() {
        return "item.stardewcraft.corn";
    }
}
