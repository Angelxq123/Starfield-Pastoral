package com.stardew.craft.item.crop;

import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.item.crop.summer.TomatoSeedItem;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

public class QiBeanItem extends TomatoSeedItem {
    public QiBeanItem(Properties properties) { super(properties); }

    @Override
    protected Block getCropBlock() { return ModBlocks.QI_FRUIT_CROP.get(); }

    @Override
    protected boolean isPlantingAllowed(Level level, BlockPos pos, int season) { return true; }

    @Override
    public int getSellPrice(ItemStack stack) { return 1; }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!context.getLevel().getBlockState(context.getClickedPos().above(2)).isAir()) return InteractionResult.PASS;
        return super.useOn(context);
    }
}
