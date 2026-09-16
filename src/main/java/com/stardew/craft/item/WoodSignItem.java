package com.stardew.craft.item;

import com.stardew.craft.block.ModBlocks;
import net.minecraft.core.Direction;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.Map;

@SuppressWarnings("null")
public final class WoodSignItem extends StardewBlockItem {
    public WoodSignItem(Properties properties) {
        super(ModBlocks.WOOD_SIGN.get(), "stardewcraft.type.utility", -1, properties);
    }

    @Override
    @Nullable
    protected BlockState getPlacementState(BlockPlaceContext context) {
        if (context.getClickedFace() == Direction.UP) return super.getPlacementState(context);
        if (!context.getClickedFace().getAxis().isHorizontal()) return null;
        BlockState state = ModBlocks.WOOD_WALL_SIGN.get().getStateForPlacement(context);
        return state != null && canPlace(context, state) ? state : null;
    }

    @Override
    public void registerBlocks(Map<Block, Item> blockToItemMap, Item item) {
        super.registerBlocks(blockToItemMap, item);
        blockToItemMap.put(ModBlocks.WOOD_WALL_SIGN.get(), item);
    }
}
