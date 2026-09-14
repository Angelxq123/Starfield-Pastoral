package com.stardew.craft.tree;

import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.blockentity.NewTreePartBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class WildTrees {

	private WildTrees() {
	}

	public record Def(
			String id,
			Supplier<Block> sapling0,
			Supplier<Block> sapling1,
			Supplier<Block> modernRoot,
			Supplier<Block> modernLog,
			Supplier<Block> modernLeaves,
			Supplier<Block> modernBranch,
			float growthChance,
			float fertilizedGrowthChance,
			float seedSpreadChance,
			float seedOnShakeChance,
			float seedOnChopChance,
			boolean growsInWinter
	) {
		public boolean isAnyPart(BlockState state) {
			return isModernPart(state);
	}

		public boolean isModernRoot(BlockState state) {
			return state.getBlock() == modernRoot.get();
		}

		public boolean isModernLog(BlockState state) {
			return state.getBlock() == modernLog.get();
		}

		public boolean isModernLeaves(BlockState state) {
			return state.getBlock() == modernLeaves.get();
		}

		public boolean isModernBranch(BlockState state) {
			return state.getBlock() == modernBranch.get();
		}

		public boolean isModernPart(BlockState state) {
			Block b = state.getBlock();
			return b == modernRoot.get()
					|| b == modernLog.get()
					|| b == modernLeaves.get()
					|| b == modernBranch.get();
		}

		public boolean isSapling(BlockState state) {
			Block b = state.getBlock();
			return b == sapling0.get() || b == sapling1.get();
		}
	}

	public static final Def OAK = new Def(
			"oak",
			() -> ModBlocks.WILD_OAK_SAPLING0.get(),
			() -> ModBlocks.WILD_OAK_SAPLING1.get(),
			() -> ModBlocks.OAK_ROOT.get(),
			() -> ModBlocks.OAK_LOG.get(),
			() -> ModBlocks.OAK_LEAVES.get(),
			() -> ModBlocks.OAK_BRANCH.get(),
			0.2f,
			1.0f,
			0.15f,
			0.05f,
			0.75f,
			false
	);

	public static final Def MAPLE = new Def(
			"maple",
			() -> ModBlocks.WILD_MAPLE_SAPLING0.get(),
			() -> ModBlocks.WILD_MAPLE_SAPLING1.get(),
			() -> ModBlocks.MAPLE_ROOT.get(),
			() -> ModBlocks.MAPLE_LOG.get(),
			() -> ModBlocks.MAPLE_LEAVES.get(),
			() -> ModBlocks.MAPLE_BRANCH.get(),
			0.2f,
			1.0f,
			0.15f,
			0.05f,
			0.75f,
			false
	);

	public static final Def PINE = new Def(
			"pine",
			() -> ModBlocks.WILD_PINE_SAPLING0.get(),
			() -> ModBlocks.WILD_PINE_SAPLING1.get(),
			() -> ModBlocks.PINE_ROOT.get(),
			() -> ModBlocks.PINE_LOG.get(),
			() -> ModBlocks.PINE_LEAVES.get(),
			() -> ModBlocks.PINE_BRANCH.get(),
			0.2f,
			1.0f,
			0.15f,
			0.05f,
			0.75f,
			false
	);

	public static final Def MAHOGANY = new Def(
			"mahogany",
			() -> ModBlocks.WILD_MAHOGANY_SAPLING0.get(),
			() -> ModBlocks.WILD_MAHOGANY_SAPLING1.get(),
			() -> ModBlocks.MAHOGANY_ROOT.get(),
			() -> ModBlocks.MAHOGANY_LOG.get(),
			() -> ModBlocks.MAHOGANY_LEAVES.get(),
			() -> ModBlocks.MAHOGANY_BRANCH.get(),
			0.15f,
			0.6f,
			0.15f,
			0.05f,
			0.5625f,
			false
	);

	public static final Def MYSTIC_TREE = new Def(
			"mystic_tree",
			() -> ModBlocks.WILD_MYSTIC_TREE_SAPLING0.get(),
			() -> ModBlocks.WILD_MYSTIC_TREE_SAPLING1.get(),
			() -> ModBlocks.MYSTIC_TREE_ROOT.get(),
			() -> ModBlocks.MYSTIC_TREE_LOG.get(),
			() -> ModBlocks.MYSTIC_TREE_LEAVES.get(),
			() -> ModBlocks.MYSTIC_TREE_BRANCH.get(),
			0.15f,
			0.3f,
			0.0f,
			0.0f,
			0.0f,
			false
	);

	public static final List<Def> ALL = List.of(OAK, MAPLE, PINE, MAHOGANY, MYSTIC_TREE);

	public static Def findByAnyPart(BlockState state) {
		for (Def def : ALL) {
			if (def.isAnyPart(state)) {
				return def;
			}
		}
		return null;
	}

	public static Def findBySapling(BlockState state) {
		for (Def def : ALL) {
			if (def.isSapling(state)) {
				return def;
			}
		}
		return null;
	}

	public static boolean isAnyWildTreePart(BlockState state) {
		return findByAnyPart(state) != null;
	}

	public static Def findByModernPart(BlockState state) {
		for (Def def : ALL) {
			if (def.isModernPart(state)) {
				return def;
			}
		}
		return null;
	}

	public static Def findByModernLog(BlockState state) {
		for (Def def : ALL) {
			if (def.isModernLog(state)) {
				return def;
			}
		}
		return null;
	}

	public static Def findByModernRoot(BlockState state) {
		for (Def def : ALL) {
			if (def.isModernRoot(state)) {
				return def;
			}
		}
		return null;
	}

	public static Def findTapperSupportDef(LevelReader level, BlockPos supportPos) {
		BlockState state = level.getBlockState(supportPos);
		Def def = findByModernLog(state);
		if (def == null) def = findByModernRoot(state);
		return def != null && findTapperTreeRoot(level, supportPos) != null ? def : null;
	}

	public static BlockPos findTapperTreeRoot(LevelReader level, BlockPos supportPos) {
		BlockState state = level.getBlockState(supportPos);
		Def def = findByModernLog(state);
		if (def == null) def = findByModernRoot(state);
		if (def == null) return null;
		if (level instanceof ServerLevel serverLevel) {
			var tree = com.stardew.craft.tree.prefab.PrefabTreeRegistry.get(serverLevel).getByMember(supportPos);
			return tree != null && !tree.felled() && def.id().equals(tree.species())
					&& def.isModernRoot(level.getBlockState(tree.root())) ? tree.root() : null;
		}
		NewTreePartBlockEntity marker = getGeneratedModernTreeMarker(level, supportPos, def);
		return marker != null && isLiveGeneratedModernTreePart(level, supportPos, def)
				? marker.getGeneratedTreeRoot() : null;
	}

	public static BlockPos findModernRootFromLog(LevelReader level, BlockPos logPos, Def def) {
		if (!def.isModernLog(level.getBlockState(logPos))) {
			return null;
		}
		return findModernRootFromWood(level, logPos, def);
	}

	public static boolean isModernCompleteTree(LevelReader level, BlockPos rootPos, Def def) {
		if (!def.isModernRoot(level.getBlockState(rootPos)) || !(level instanceof ServerLevel serverLevel)) {
			return false;
		}
		var prefab = com.stardew.craft.tree.prefab.PrefabTreeRegistry.get(serverLevel).getByRoot(rootPos);
		return prefab != null && !prefab.felled() && def.id().equals(prefab.species());
	}

	public static void forEachModernLogInTree(LevelReader level, BlockPos rootPos, Def def, Consumer<BlockPos> consumer) {
		if (!def.isModernRoot(level.getBlockState(rootPos))) {
			return;
		}
		for (BlockPos pos : collectPrefabWood(level, rootPos, def)) {
			if (def.isModernLog(level.getBlockState(pos))) {
				consumer.accept(pos);
			}
		}
	}

	public static void forEachLiveGeneratedModernLogInTree(LevelReader level, BlockPos rootPos, Def def, Consumer<BlockPos> consumer) {
		NewTreePartBlockEntity rootMarker = getGeneratedModernTreeMarker(level, rootPos, def);
		if (rootMarker == null || !rootPos.equals(rootMarker.getGeneratedTreeRoot())) {
			return;
		}
		for (BlockPos pos : collectPrefabWood(level, rootPos, def)) {
			BlockState state = level.getBlockState(pos);
			if (!def.isModernLog(state)) {
				continue;
			}
			NewTreePartBlockEntity partMarker = getGeneratedModernTreeMarker(level, pos, def);
			if (partMarker != null && rootMarker.getGeneratedTreeId().equals(partMarker.getGeneratedTreeId())) {
				consumer.accept(pos);
			}
		}
	}

	public static boolean markGeneratedModernTree(ServerLevel level, BlockPos rootPos, Def def) {
		if (!isModernCompleteTree(level, rootPos, def)) {
			return false;
		}
		UUID treeId = UUID.randomUUID();
		for (BlockPos pos : collectPrefabWood(level, rootPos, def)) {
			if (level.getBlockEntity(pos) instanceof NewTreePartBlockEntity treePart) {
				treePart.markGeneratedTree(treeId, def.id(), rootPos);
			}
		}
		return true;
	}

	public static BlockPos findGeneratedModernRoot(LevelReader level, BlockPos pos, Def def) {
		if (level instanceof ServerLevel serverLevel) {
			var prefab = com.stardew.craft.tree.prefab.PrefabTreeRegistry.get(serverLevel).getByMember(pos);
			return prefab != null && def.id().equals(prefab.species()) ? prefab.root() : null;
		}
		NewTreePartBlockEntity marker = getGeneratedModernTreeMarker(level, pos, def);
		return marker != null ? marker.getGeneratedTreeRoot() : null;
	}

	private static boolean isLiveGeneratedModernTreePart(LevelReader level, BlockPos pos, Def def) {
		NewTreePartBlockEntity marker = getGeneratedModernTreeMarker(level, pos, def);
		if (marker == null) {
			return false;
		}
		BlockPos root = marker.getGeneratedTreeRoot();
		if (root == null || !def.isModernRoot(level.getBlockState(root))) {
			return false;
		}
		if (level instanceof ServerLevel serverLevel) {
			var prefab = com.stardew.craft.tree.prefab.PrefabTreeRegistry.get(serverLevel).getByMember(pos);
			if (prefab == null || prefab.felled() || !root.equals(prefab.root())) {
				return false;
			}
		}
		NewTreePartBlockEntity rootMarker = getGeneratedModernTreeMarker(level, root, def);
		return rootMarker != null
				&& root.equals(rootMarker.getGeneratedTreeRoot())
				&& marker.getGeneratedTreeId().equals(rootMarker.getGeneratedTreeId());
	}

	private static NewTreePartBlockEntity getGeneratedModernTreeMarker(LevelReader level, BlockPos pos, Def def) {
		if (!isModernWood(level.getBlockState(pos), def)) {
			return null;
		}
		if (!(level.getBlockEntity(pos) instanceof NewTreePartBlockEntity marker)) {
			return null;
		}
		if (!marker.hasGeneratedTreeMarker()) {
			return null;
		}
		return def.id().equals(marker.getGeneratedTreeSpecies()) ? marker : null;
	}

	private static BlockPos findModernRootFromWood(LevelReader level, BlockPos start, Def def) {
		return findGeneratedModernRoot(level, start, def);
	}

	private static Set<BlockPos> collectPrefabWood(LevelReader level, BlockPos start, Def def) {
		Set<BlockPos> wood = new HashSet<>();
		if (level instanceof ServerLevel serverLevel) {
			var prefab = com.stardew.craft.tree.prefab.PrefabTreeRegistry.get(serverLevel).getByMember(start);
			if (prefab != null && def.id().equals(prefab.species())) {
				for (BlockPos member : prefab.members()) {
					if (isModernWood(level.getBlockState(member), def)) {
						wood.add(member.immutable());
					}
				}
			}
		}
		return wood;
	}

	private static boolean isModernWood(BlockState state, Def def) {
		return def.isModernRoot(state) || def.isModernLog(state) || def.isModernBranch(state);
	}

	public static void requireNonNullBlocks() {
		// Defensive helper for early crash if registrations are missing.
		for (Def def : ALL) {
			Objects.requireNonNull(def.sapling0().get());
			Objects.requireNonNull(def.sapling1().get());
			Objects.requireNonNull(def.modernRoot().get());
			Objects.requireNonNull(def.modernLog().get());
			Objects.requireNonNull(def.modernLeaves().get());
			Objects.requireNonNull(def.modernBranch().get());
		}
	}
}
