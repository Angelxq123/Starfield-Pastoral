package com.stardew.craft.item.tree;

import com.stardew.craft.item.SimpleStardewItem;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.function.Supplier;

public class TreeSeedItem extends SimpleStardewItem {
	private final Supplier<? extends Block> sapling0;

	public TreeSeedItem(Supplier<? extends Block> sapling0, int sellPrice, Properties properties) {
		super("stardewcraft.type.seed", sellPrice, properties);
		this.sapling0 = sapling0;
	}

	@SuppressWarnings("null")
	@Override
	public InteractionResult useOn(@SuppressWarnings("null") UseOnContext context) {
		Level level = context.getLevel();
		BlockPos groundPos = context.getClickedPos();
		@SuppressWarnings("null")
		BlockState groundState = level.getBlockState(groundPos);

		if (!isPlantableGround(groundState)) {
			return InteractionResult.PASS;
		}

		BlockPos placePos = groundPos.above();
		if (!level.getBlockState(placePos).canBeReplaced()) {
			return InteractionResult.PASS;
		}

		BlockState saplingState = sapling0.get().defaultBlockState();
		if (!saplingState.canSurvive(level, placePos)) {
			return InteractionResult.PASS;
		}

		if (!level.isClientSide) {
			level.setBlock(placePos, saplingState, 3);
			level.playSound(null, placePos, SoundEvents.GRASS_PLACE, SoundSource.BLOCKS, 0.9F, 1.0F);

			if (context.getPlayer() == null || !context.getPlayer().isCreative()) {
				ItemStack stack = context.getItemInHand();
				stack.shrink(1);
			}
		}

		return InteractionResult.sidedSuccess(level.isClientSide);
	}

	@SuppressWarnings("null")
	private static boolean isPlantableGround(BlockState state) {
		return com.stardew.craft.block.terrain.TerrainSoils.treeSeedGround(state);
	}
}
