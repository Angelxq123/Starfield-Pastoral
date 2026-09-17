package com.stardew.craft.block.tree;

import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.core.ModDimensions;
import com.stardew.craft.time.StardewTimeManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class StardewLeavesBlock extends LeavesBlock {
	private static final int FAST_DECAY_DELAY = 3;
	private static volatile int clientSeason = -1;

	public StardewLeavesBlock(Properties properties) {
		super(properties);
	}

	public static void updateClientSeason(int season) {
		clientSeason = season;
	}

	/** Dormancy is visual/physical only: never delete a prefab's saved leaves. */
	public static boolean dormant(BlockState state, BlockGetter getter) {
		if (!(getter instanceof Level level) || !level.dimension().equals(ModDimensions.STARDEW_VALLEY)) return false;
		int season = level instanceof ServerLevel ? StardewTimeManager.get().getCurrentSeason() : clientSeason;
		return season == 3 && (state.is(ModBlocks.OAK_LEAVES.get())
				|| state.is(ModBlocks.MAPLE_LEAVES.get()) || state.is(ModBlocks.MAHOGANY_LEAVES.get())
				|| state.is(ModBlocks.POINTED_LEAVES.get()));
	}

	@Override
	public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return dormant(state, level) ? Shapes.empty() : super.getShape(state, level, pos, context);
	}

	@Override
	public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return dormant(state, level) ? Shapes.empty() : super.getCollisionShape(state, level, pos, context);
	}

	@SuppressWarnings("null")
	@Override
	protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
		BlockState updated = super.updateShape(state, direction, neighborState, level, pos, neighborPos);
		if (shouldFastDecay(updated)) {
			level.scheduleTick(pos, this, FAST_DECAY_DELAY);
		}
		return updated;
	}

	@SuppressWarnings("null")
	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		super.tick(state, level, pos, random);
		BlockState current = level.getBlockState(pos);
		if (current.is(this) && shouldFastDecay(current)) {
			level.destroyBlock(pos, true);
		}
	}

	@SuppressWarnings("null")
	@Override
	protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		super.randomTick(state, level, pos, random);
		BlockState current = level.getBlockState(pos);
		if (current.is(this) && shouldFastDecay(current)) {
			level.scheduleTick(pos, this, FAST_DECAY_DELAY + random.nextInt(4));
		}
	}

	private static boolean shouldFastDecay(BlockState state) {
		return state.hasProperty(PERSISTENT)
				&& state.hasProperty(DISTANCE)
				&& !state.getValue(PERSISTENT)
				&& state.getValue(DISTANCE) >= DECAY_DISTANCE;
	}
}
