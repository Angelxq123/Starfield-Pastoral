package com.stardew.craft.block.crop;

import com.stardew.craft.item.ModItems;
import com.stardew.craft.item.quality.QualityHelper;
import java.util.function.Supplier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.util.RandomSource;

/** Qi fruit keeps all four one-day phases and always yields normal-quality fruit. */
public class QiFruitCropBlock extends TomatoCropBlock {
    private static final int[] PHASE_DAYS = {1, 1, 1, 1};

    @Override
    protected Supplier<Item> getSeedsItem() { return ModItems.VANILLA_CATEGORY_ITEMS.get("qi_bean"); }

    @Override
    protected Supplier<Item> getCropItem() { return ModItems.VANILLA_CATEGORY_ITEMS.get("qi_fruit"); }

    @Override
    protected int[] getPhaseDays() { return PHASE_DAYS; }

    @Override
    protected boolean isInSeason(Level level) { return true; }

    @Override
    protected boolean canRegrow() { return false; }

    @Override
    protected int getRegrowDays() { return 0; }

    @Override
    protected int getRegrowAge() { return 0; }

    @Override
    protected int getHarvestQuality(RandomSource random, int fertilizerLevel, int farmingLevel) { return QualityHelper.NORMAL; }

    @Override
    protected ItemStack getHarvestItem(int quality) { return new ItemStack(getCropItem().get()); }

    @Override
    public String getCropDisplayNameKey() { return "item.stardewcraft.qi_fruit"; }
}
