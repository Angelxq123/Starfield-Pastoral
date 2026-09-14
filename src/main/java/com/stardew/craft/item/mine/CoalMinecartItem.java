package com.stardew.craft.item.mine;

import com.stardew.craft.block.mine.MineRailBlock;
import com.stardew.craft.entity.ModEntities;
import com.stardew.craft.item.SimpleStardewItem;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.state.properties.RailShape;

@SuppressWarnings("null")
public final class CoalMinecartItem extends SimpleStardewItem {
    private final boolean loaded;
    public CoalMinecartItem(boolean loaded, Properties properties) {
        super("stardewcraft.type.building", -1, properties);
        this.loaded = loaded;
    }

    @Override public InteractionResult useOn(UseOnContext context) {
        var level = context.getLevel();
        var pos = context.getClickedPos();
        var state = level.getBlockState(pos);
        boolean rail = state.getBlock() instanceof MineRailBlock;
        if (!rail) {
            if (context.getClickedFace() != Direction.UP) return InteractionResult.FAIL;
            pos = pos.above();
        } else if (state.getValue(MineRailBlock.SHAPE) != RailShape.NORTH_SOUTH
                && state.getValue(MineRailBlock.SHAPE) != RailShape.EAST_WEST) return InteractionResult.FAIL;
        var cart = ModEntities.COAL_MINECART.get().create(level);
        if (cart == null) return InteractionResult.FAIL;
        float yaw = rail ? (state.getValue(MineRailBlock.SHAPE) == RailShape.EAST_WEST ? 90 : 0)
                : context.getHorizontalDirection().toYRot();
        cart.setYRot(yaw);
        cart.setPos(pos.getX() + 0.5, pos.getY() + (rail ? 4.0 / 16 : 0), pos.getZ() + 0.5);
        cart.setLoaded(loaded);
        if (!level.noCollision(cart) || !level.getEntities(cart, cart.getBoundingBox(), e -> e.canBeCollidedWith()).isEmpty())
            return InteractionResult.FAIL;
        if (!level.isClientSide) {
            level.addFreshEntity(cart);
            if (context.getPlayer() == null || !context.getPlayer().getAbilities().instabuild)
                context.getItemInHand().shrink(1);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
