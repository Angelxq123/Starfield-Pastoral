package com.stardew.craft.item.crop;

import com.stardew.craft.api.v1.world.StardewLocation;
import com.stardew.craft.api.v1.world.StardewLocations;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.greenhouse.GreenhouseManager;
import com.stardew.craft.item.crop.summer.TomatoSeedItem;
import com.stardew.craft.mining.IslandStoneRewards;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

/** Source rule: indoors or Ginger Island, in any season. */
public class CactusSeedItem extends TomatoSeedItem {
    public CactusSeedItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    protected Block getCropBlock() {
        return ModBlocks.CACTUS_FRUIT_CROP.get();
    }

    @Override
    public int getSellPrice(ItemStack stack) {
        return 0;
    }

    @Override
    protected boolean isPlantingAllowed(Level level, BlockPos pos, int season) {
        return GreenhouseManager.isInGreenhouseInterior(level, pos)
                || StardewLocations.find(level, pos).map(StardewLocation::indoor).orElse(false)
                || StardewLocations.hierarchy(level.dimension().location(), pos).stream().anyMatch(IslandStoneRewards::isIsland);
    }

    @Override
    protected String getPlantingDeniedMessageKey() {
        return "stardewcraft.message.seed.cactus_outside";
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!context.getLevel().getBlockState(context.getClickedPos().above(2)).isAir()) {
            return InteractionResult.PASS;
        }
        return super.useOn(context);
    }
}
