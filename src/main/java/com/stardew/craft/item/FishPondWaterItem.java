package com.stardew.craft.item;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;

/** A creative building tool; pond water cannot be collected with buckets. */
public class FishPondWaterItem extends StardewBlockItem {
    public FishPondWaterItem(Block block, Properties properties) {
        super(block, "stardewcraft.type.utility", -1, properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        return context.getPlayer() != null && context.getPlayer().isCreative()
                ? super.useOn(context) : InteractionResult.FAIL;
    }
}
