package com.stardew.craft.tree.prefab;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.manager.WildTreeSeedManager;
import com.stardew.craft.mining.StructureLoader;
import com.stardew.craft.tree.WildTrees;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 预制树放置：读取 NBT 结构，把树根对齐到世界坐标后摆放全部方块，并登记占地。
 *
 * <p>放置时的关键约定：
 * <ul>
 *   <li>结构中必须恰有该树种的 {@code *_root} 方块（树根，固定在 y=0 底层），它决定与世界坐标的对齐基准；</li>
 *   <li>树叶强制 {@code PERSISTENT=true}，避免树站立时被原版衰减逻辑误判掉落；</li>
 *   <li>用 {@code UPDATE_CLIENTS | UPDATE_KNOWN_SHAPE} 放置，不触发邻居更新，
 *       以忠实保留结构里栏杆/栅栏/玻璃板等的连接状态；</li>
 *   <li>放置后登记占地 + trackTree（参与每日带种/扩散）+ 打 generated 标记（让采集器等子系统识别）。</li>
 * </ul>
 */
public final class PrefabTreeManager {
	private PrefabTreeManager() {
	}

	private static final int PLACE_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

	/** 已解析结构缓存（资源在运行期不变，可安全缓存）。 */
	private static final Map<String, StructureLoader.StructureBlocks> CACHE = new ConcurrentHashMap<>();

	private static StructureLoader.StructureBlocks structure(int speciesIndex, int variant) {
		String path = PrefabTrees.structurePath(speciesIndex, variant);
		StructureLoader.StructureBlocks cached = CACHE.get(path);
		if (cached != null) {
			return cached;
		}
		StructureLoader.StructureBlocks parsed = StructureLoader.readStructureNbtBlocks(path);
		if (parsed != null) {
			CACHE.put(path, parsed);
		}
		return parsed;
	}

	/** 找到结构中树根方块相对原点的偏移；缺失或多于 1 个时按约定处理（多于 1 取首个并警告）。 */
	private static BlockPos findRootRel(StructureLoader.StructureBlocks schem, Block rootBlock, String pathForLog) {
		BlockPos rootRel = null;
		int rootCount = 0;
		for (StructureLoader.PositionedState ps : schem.states()) {
			if (ps.state().getBlock() == rootBlock) {
				rootCount++;
				if (rootRel == null) {
					rootRel = new BlockPos(ps.dx(), ps.dy(), ps.dz());
				}
			}
		}
		if (rootRel == null) {
			StardewCraft.LOGGER.error("Prefab tree structure {} has no root block", pathForLog);
			return null;
		}
		if (rootCount > 1) {
			StardewCraft.LOGGER.warn("Prefab tree structure {} has {} root blocks; expected exactly 1. Using the first.", pathForLog, rootCount);
		}
		return rootRel;
	}

	/**
	 * 在 {@code worldRootPos}（树根所在格）放置指定树种的指定变体。
	 *
	 * @param validate true 时会先校验占地（除树根格外都必须可替换），失败则不放置
	 * @return 是否成功放置
	 */
	public static boolean place(ServerLevel level, BlockPos worldRootPos, WildTrees.Def def, int variant, boolean validate) {
		int speciesIndex = PrefabTrees.speciesIndex(def);
		if (speciesIndex == 0) {
			return false;
		}
		StructureLoader.StructureBlocks schem = structure(speciesIndex, variant);
		if (schem == null) {
			return false;
		}
		BlockPos rootRel = findRootRel(schem, def.modernRoot().get(), PrefabTrees.structurePath(speciesIndex, variant));
		if (rootRel == null) {
			return false;
		}
		BlockPos origin = worldRootPos.subtract(rootRel);

		if (validate && !canPlace(level, origin, worldRootPos, schem)) {
			return false;
		}

		Set<BlockPos> members = new HashSet<>(schem.states().size());
		for (StructureLoader.PositionedState ps : schem.states()) {
			BlockPos wp = origin.offset(ps.dx(), ps.dy(), ps.dz());
			BlockState state = ps.state();
			if (state.hasProperty(BlockStateProperties.PERSISTENT)) {
				state = state.setValue(BlockStateProperties.PERSISTENT, Boolean.TRUE);
			}
			level.setBlock(wp, state, PLACE_FLAGS);
			members.add(wp.immutable());
		}

		PrefabTreeRegistry.get(level).register(worldRootPos, def.id(), variant, members);
		// 让预制树参与「每日带种 / 摇树掉种 / 种子扩散」（与现成的 WildTreeShakeEvents 复用）。
		WildTreeSeedManager.get(level).trackTree(level, worldRootPos, def);
		// 按登记的木质成员写入同一棵树的标记，供采集器识别。
		WildTrees.markGeneratedModernTree(level, worldRootPos, def);
		return true;
	}

	/** Repair old map trees only after matching an entire shipped prefab, never loose placed wood. */
	public static PrefabTreeInstance findOrRestoreForTapper(ServerLevel level, BlockPos clicked) {
		PrefabTreeRegistry registry = PrefabTreeRegistry.get(level);
		PrefabTreeInstance known = registry.getByMember(clicked);
		if (known != null) return known;
		BlockState clickedState = level.getBlockState(clicked);
		WildTrees.Def def = WildTrees.findByAnyPart(clickedState);
		if (def == null) {
			for (WildTrees.Def candidate : WildTrees.ALL) {
				if (PrefabTrees.countsAsLog(candidate, clickedState.getBlock())) { def = candidate; break; }
			}
		}
		if (def == null) return null;
		// Largest shipped canopy is nine blocks wide; tallest tree is twenty high.
		for (BlockPos root : BlockPos.betweenClosed(clicked.offset(-5, -24, -5), clicked.offset(5, 0, 5))) {
			if (!level.hasChunkAt(root) || !def.isModernRoot(level.getBlockState(root))) continue;
			// Never adopt a felled tree or attach player-added wood to an existing tree.
			if (registry.getByRoot(root) != null) continue;
			for (int variant = 1; variant <= PrefabTrees.VARIANTS_PER_SPECIES; variant++) {
				var layout = structure(PrefabTrees.speciesIndex(def), variant);
				if (layout == null) continue;
				BlockPos relativeRoot = findRootRel(layout, def.modernRoot().get(), "tapper legacy lookup");
				if (relativeRoot == null) continue;
				BlockPos origin = root.subtract(relativeRoot);
				Set<BlockPos> members = new HashSet<>();
				boolean matches = true;
				for (StructureLoader.PositionedState part : layout.states()) {
					if (part.state().isAir()) continue;
					BlockPos pos = origin.offset(part.dx(), part.dy(), part.dz());
					if (!level.hasChunkAt(pos) || !level.getBlockState(pos).is(part.state().getBlock())
							|| registry.getByMember(pos) != null) { matches = false; break; }
					members.add(pos.immutable());
				}
				if (!matches || !members.contains(clicked)) continue;
				registry.register(root, def.id(), variant, members);
				WildTrees.markGeneratedModernTree(level, root, def);
				WildTreeSeedManager.get(level).trackTree(level, root, def);
				return registry.getByRoot(root);
			}
		}
		return null;
	}

	/** 随机挑一个变体放置（校验占地）。用于树苗成熟时生成。 */
	public static boolean tryPlaceRandomVariant(ServerLevel level, BlockPos worldRootPos, WildTrees.Def def) {
		RandomSource random = level.random;
		List<Integer> order = new ArrayList<>(PrefabTrees.VARIANTS_PER_SPECIES);
		for (int v = 1; v <= PrefabTrees.VARIANTS_PER_SPECIES; v++) {
			order.add(v);
		}
		java.util.Collections.shuffle(order, new java.util.Random(random.nextLong()));
		for (int variant : order) {
			if (place(level, worldRootPos, def, variant, true)) {
				return true;
			}
		}
		return false;
	}

	/** 是否至少有一个变体能在此处放下（只读校验，不放置）。用于树苗成熟门槛判定。 */
	public static boolean canPlaceAnyVariant(ServerLevel level, BlockPos worldRootPos, WildTrees.Def def) {
		int speciesIndex = PrefabTrees.speciesIndex(def);
		if (speciesIndex == 0) {
			return false;
		}
		Block rootBlock = def.modernRoot().get();
		for (int v = 1; v <= PrefabTrees.VARIANTS_PER_SPECIES; v++) {
			StructureLoader.StructureBlocks schem = structure(speciesIndex, v);
			if (schem == null) {
				continue;
			}
			BlockPos rootRel = findRootRel(schem, rootBlock, PrefabTrees.structurePath(speciesIndex, v));
			if (rootRel == null) {
				continue;
			}
			if (canPlace(level, worldRootPos.subtract(rootRel), worldRootPos, schem)) {
				return true;
			}
		}
		return false;
	}

	private static boolean canPlace(ServerLevel level, BlockPos origin, BlockPos worldRootPos, StructureLoader.StructureBlocks schem) {
		for (StructureLoader.PositionedState ps : schem.states()) {
			BlockPos wp = origin.offset(ps.dx(), ps.dy(), ps.dz());
			// 树根格允许覆盖（树苗成熟时它就站在那里）。
			if (wp.equals(worldRootPos)) {
				continue;
			}
			if (!level.getBlockState(wp).canBeReplaced()) {
				return false;
			}
		}
		return true;
	}
}
