package com.stardew.craft.item;

import com.stardew.craft.time.StardewTimeManager;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;

/** SDV Crop.getRandomFlowerSeedForThisSeason; the selected seed retains its existing planting rules. */
public final class MixedFlowerSeedsItem extends Item implements IStardewItem {
    public MixedFlowerSeedsItem(Properties properties) { super(properties); }
    @Override public String getItemTypeKey() { return "stardewcraft.type.seed"; }
    @Override public int getSellPrice(ItemStack stack) { return -1; }

    public static Item pickSeed(int season, RandomSource random) {
        if (season == 3) season = random.nextInt(3);
        return switch (season) {
            case 0 -> random.nextInt(2) == 0 ? ModItems.TULIP_SEEDS.get() : ModItems.BLUE_JAZZ_SEEDS.get();
            case 1 -> switch (random.nextInt(3)) {
                case 0 -> ModItems.SUMMER_SPANGLE_SEEDS.get();
                case 1 -> ModItems.POPPY_SEEDS.get();
                default -> ModItems.SUNFLOWER_SEEDS.get();
            };
            case 2 -> random.nextInt(2) == 0 ? ModItems.SUNFLOWER_SEEDS.get() : ModItems.FAIRY_ROSE_SEEDS.get();
            default -> throw new IllegalArgumentException("Invalid season " + season);
        };
    }

    @Override public InteractionResult useOn(UseOnContext context) {
        // Predict the shared soil/space checks without advancing a client-side random flower selection.
        Item seed = context.getLevel().isClientSide ? ModItems.TULIP_SEEDS.get()
                : pickSeed(StardewTimeManager.get().getCurrentSeason(), context.getLevel().random);
        return seed.useOn(context);
    }
}
