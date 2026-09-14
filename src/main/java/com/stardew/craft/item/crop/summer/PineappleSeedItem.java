package com.stardew.craft.item.crop.summer;

import com.stardew.craft.block.ModBlocks;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;

public class PineappleSeedItem extends TomatoSeedItem {
    public PineappleSeedItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    protected Block getCropBlock() {
        return ModBlocks.PINEAPPLE_CROP.get();
    }

    @Override
    public int getSellPrice(ItemStack stack) {
        return 240;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        // The mature crown has an upper interaction carrier; never replace a block above it.
        if (!context.getLevel().getBlockState(context.getClickedPos().above(2)).isAir()) {
            return InteractionResult.PASS;
        }
        return super.useOn(context);
    }
}
