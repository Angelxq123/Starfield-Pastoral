package com.stardew.craft.item.crop.summer;

import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.item.crop.spring.RiceShootItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

/** Uses the existing paddy planting checks, including space for the upper carrier. */
public class TaroTuberItem extends RiceShootItem {
    public TaroTuberItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    protected Block getCropBlock() {
        return ModBlocks.TARO_ROOT_CROP.get();
    }

    @Override
    protected int getPlantingSeason() {
        return 1;
    }
}
