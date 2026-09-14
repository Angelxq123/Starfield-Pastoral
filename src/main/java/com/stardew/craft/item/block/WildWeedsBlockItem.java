package com.stardew.craft.item.block;

import com.stardew.craft.StardewCraft;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 野草方块物品，携带季节和变体信息
 */
public class WildWeedsBlockItem extends BlockItem implements com.stardew.craft.item.IStardewItem {
    @Override
    public String getItemTypeKey() {
        return "stardewcraft.type.natural_grass";
    }

    private final int season;
    private final int variant;

    @SuppressWarnings("null")
    public WildWeedsBlockItem(Block block, int season, int variant, Properties properties) {
        super(block, properties);
        this.season = season;
        this.variant = variant;
    }

    @SuppressWarnings("null")
    @Override
    protected boolean updateCustomBlockEntityTag(@SuppressWarnings("null") net.minecraft.core.BlockPos pos, @SuppressWarnings("null") net.minecraft.world.level.Level level,
            @SuppressWarnings("null") net.minecraft.world.entity.player.Player player, @SuppressWarnings("null") ItemStack stack, @SuppressWarnings("null") BlockState state) {
        return super.updateCustomBlockEntityTag(pos, level, player, stack, state);
    }

    /**
     * 兼容旧 seasonal 物品 id：统一显示为“杂草”。
     */
    @Override
    public String getDescriptionId() {
        return "block." + StardewCraft.MODID + ".wild_weeds";
    }

    /**
     * 放置时交给方块本身决定季节/变体，保证行为与统一杂草物品一致。
     */
    @SuppressWarnings("null")
    @Override
    protected BlockState getPlacementState(@SuppressWarnings("null") net.minecraft.world.item.context.BlockPlaceContext context) {
        return super.getPlacementState(context);
    }

    public int getSeason() {
        return season;
    }

    public int getVariant() {
        return variant;
    }
}
