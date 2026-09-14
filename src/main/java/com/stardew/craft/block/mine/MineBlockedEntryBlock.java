package com.stardew.craft.block.mine;

import com.stardew.craft.block.decor.MapDecorStaticBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/** One sealed entrance; its top extension renders the section above Java's 32-pixel limit. */
public final class MineBlockedEntryBlock extends MapDecorStaticBlock {
    public static final BooleanProperty UPPER = BooleanProperty.create("upper");

    public MineBlockedEntryBlock(Properties properties) {
        super(properties, "block/mine_blocked_entry", 0, 0, 0, 32, 48, 16);
        registerDefaultState(defaultBlockState().setValue(UPPER, false).setValue(MineBuildingTheme.PROPERTY, MineBuildingTheme.EARTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(UPPER, MineBuildingTheme.PROPERTY);
    }

    @Override public BlockState getStateForPlacement(net.minecraft.world.item.context.BlockPlaceContext context) {
        BlockState state = super.getStateForPlacement(context);
        return state == null ? null : state.setValue(MineBuildingTheme.PROPERTY, MineBuildingTheme.forPlacement(context));
    }

    @Override public net.minecraft.world.item.ItemStack getCloneItemStack(net.minecraft.world.level.LevelReader level, BlockPos pos, BlockState state) {
        return MineBuildingTheme.picked(this, state);
    }

    @Override
    protected BlockState extensionState(BlockState mainState, BlockPos offset) {
        return super.extensionState(mainState, offset)
                .setValue(UPPER, offset.equals(new BlockPos(0, 2, 0)));
    }

    @Override protected BlockState updateShape(BlockState state, net.minecraft.core.Direction direction, BlockState neighbor,
            net.minecraft.world.level.LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        level.scheduleTick(pos, this, 1);
        return state;
    }

    @Override protected void onPlace(BlockState state, net.minecraft.world.level.Level level, BlockPos pos, BlockState old, boolean moving) {
        super.onPlace(state, level, pos, old, moving);
        if (!level.isClientSide) level.scheduleTick(pos, this, 1);
    }

    @Override protected void tick(BlockState state, net.minecraft.server.level.ServerLevel level, BlockPos pos, net.minecraft.util.RandomSource random) {
        if (!super.canSurvive(state, level, pos)) runWithDropsSuppressed(() -> level.removeBlock(pos, false));
    }
}
