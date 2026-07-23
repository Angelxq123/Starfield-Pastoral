package com.stardew.craft.manager;

import com.stardew.craft.item.ModItems;
import com.stardew.craft.time.StardewTimeManager;
import com.stardew.craft.time.settlement.DailySettlementContext;
import com.stardew.craft.time.settlement.DailySettlementContextFactory;
import com.stardew.craft.time.settlement.DailySettlementRandom;
import com.stardew.craft.time.settlement.DailySettlementWorkUnit;
import com.stardew.craft.time.settlement.DailySettlementWorkUnits;
import com.stardew.craft.tree.WildTrees;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 野生树“种子掉落/扩散”管理器（对齐 Stardew Valley Tree.cs 的核心行为）：
 * - 每天：成熟树按 SeedOnShakeChance 随机获得 hasSeed；并按 SeedSpreadChance 扩散生成新树苗
 * - 摇树：当天未摇过且 hasSeed=true 时，掉落 1 个对应树种子，并清空 hasSeed
 * - 砍树：由砍树事件按 SeedOnChopChance 掉落 1-2 个对应树种子（本类只提供映射/状态）
 *
 * 这里用 SavedData 持久化每棵成熟树的 hasSeed/wasShakenToday，避免 chunk unload 丢状态。
 */
public class WildTreeSeedManager extends SavedData {
	private static final String DATA_NAME = "stardew_wild_tree_seed_manager";
	private static final int SEASON_FALL = 2;

	private static final class Entry {
		final String treeId;
		boolean hasSeed;
		int lastSeedRollAbsDay;
		int lastShakenAbsDay;

		private Entry(String treeId) {
			this.treeId = treeId;
			this.hasSeed = false;
			this.lastSeedRollAbsDay = Integer.MIN_VALUE;
			this.lastShakenAbsDay = Integer.MIN_VALUE;
		}
	}

	private final Map<GlobalPos, Entry> entries = new ConcurrentHashMap<>();
	private final Map<GlobalPos, Entry> pendingAdds = new HashMap<>();
	private final Set<GlobalPos> pendingRemoves = new HashSet<>();
	private boolean processing;

	@SuppressWarnings("null")
	public static WildTreeSeedManager get(ServerLevel level) {
		return level.getDataStorage().computeIfAbsent(
				new SavedData.Factory<>(WildTreeSeedManager::new, WildTreeSeedManager::load, null),
				DATA_NAME
		);
	}

	public void trackTree(ServerLevel level, BlockPos trunk0Pos, WildTrees.Def def) {
		@SuppressWarnings("null")
		GlobalPos gp = GlobalPos.of(level.dimension(), trunk0Pos.immutable());
		if (processing) {
			Entry pendingAdd = pendingAdds.get(gp);
			WildPendingState transition = FarmDailyDecisions.onWildTreeTracked(
					entries.containsKey(gp),
					pendingRemoves.contains(gp),
					pendingAdd == null ? null : pendingAdd.treeId,
					def.id());
			if (transition.pendingRemove()) {
				pendingRemoves.add(gp);
			} else {
				pendingRemoves.remove(gp);
			}
			if (transition.pendingAddTreeId() == null) {
				pendingAdds.remove(gp);
			} else if (pendingAdd == null) {
				pendingAdds.put(gp, new Entry(transition.pendingAddTreeId()));
			}
			setDirty();
			return;
		}
		entries.computeIfAbsent(gp, k -> {
			setDirty();
			return new Entry(def.id());
		});
	}

	public void untrackTree(ServerLevel level, BlockPos trunk0Pos) {
		@SuppressWarnings("null")
		GlobalPos gp = GlobalPos.of(level.dimension(), trunk0Pos.immutable());
		if (processing) {
			Entry pendingAdd = pendingAdds.get(gp);
			WildPendingState transition = FarmDailyDecisions.onWildTreeUntracked(
					entries.containsKey(gp),
					pendingRemoves.contains(gp),
					pendingAdd == null ? null : pendingAdd.treeId);
			if (transition.pendingRemove()) {
				pendingRemoves.add(gp);
			} else {
				pendingRemoves.remove(gp);
			}
			pendingAdds.remove(gp);
			setDirty();
			return;
		}
		if (entries.remove(gp) != null) {
			setDirty();
		}
	}

	public boolean tryMigrateGeneratedTreeMarker(ServerLevel level, BlockPos rootPos, WildTrees.Def def) {
		@SuppressWarnings("null")
		GlobalPos gp = GlobalPos.of(level.dimension(), rootPos.immutable());
		Entry entry = entries.get(gp);
		if (entry == null || !def.id().equals(entry.treeId)) {
			return false;
		}
		return WildTrees.markGeneratedModernTree(level, rootPos, def);
	}

	/**
	 * 右键摇树：返回 true 表示本次“摇树动作”有效（会消耗今天的摇树机会）。
	 */
	public boolean shake(ServerLevel level, BlockPos trunk0Pos, WildTrees.Def def, ServerPlayer player) {
		int absDay = getAbsDay();
		@SuppressWarnings("null")
		GlobalPos gp = GlobalPos.of(level.dimension(), trunk0Pos.immutable());
		Entry pendingAdd = pendingAdds.get(gp);
		Entry liveEntry = pendingAdd == null
				? entries.computeIfAbsent(gp, k -> new Entry(def.id()))
				: entries.get(gp);
		Entry entry = FarmDailyDecisions.routeWildShakeEntry(liveEntry, pendingAdd);

		// 同一天只允许摇一次（对齐 wasShakenToday 防重复）
		if (entry.lastShakenAbsDay == absDay) {
			return false;
		}

		ensureRolledForDay(level, trunk0Pos, def, entry, absDay);

		// 采集等级门槛：单人 >=1；多人允许 0 级拿到种子（对齐 SV 的 multiplayer 分支）。
		int foragingLevel = com.stardew.craft.player.PlayerStardewDataAPI.getSkillLevel(player, com.stardew.craft.player.SkillType.FORAGING);
		boolean canDropSeed = player.server.getPlayerList().getPlayerCount() > 1 || foragingLevel >= 1;
		WildShakeDecision shakeDecision = FarmDailyDecisions.applyWildShakeState(
				entry.hasSeed,
				entry.lastSeedRollAbsDay,
				entry.lastShakenAbsDay,
				absDay,
				canDropSeed);
		WildSeedDailyState shakenState = shakeDecision.state();
		entry.hasSeed = shakenState.hasSeed();
		entry.lastSeedRollAbsDay = shakenState.lastSeedRollAbsDay();
		entry.lastShakenAbsDay = shakenState.lastShakenAbsDay();
		if (shakeDecision.dropSeed()) {
			Item drop = getShakeDropItem(def);
			if (drop != null) {
				Block.popResource(level, trunk0Pos, new ItemStack(drop, 1));
			}
		}

		setDirty();
		return true; // 不管是否掉落，摇过就算一次
	}

	/**
	 * 每日结算入口：刷新 hasSeed，并尝试扩散生成树苗。
	 */
	@SuppressWarnings("null")
	public void onNewDay(ServerLevel level, int absDay) {
		DailySettlementWorkUnits.drain(createDailyWorkUnit(
				level, contextForAbsoluteDay(absDay)));
	}

	public DailySettlementWorkUnit createDailyWorkUnit(
			ServerLevel level,
			DailySettlementContext context) {
		if (processing) {
			throw new IllegalStateException("Wild tree seed daily work is already active");
		}
		Objects.requireNonNull(level, "level");
		Objects.requireNonNull(context, "context");
		processing = true;
		try {
			List<DailyTreeEntry> treeSnapshot = new ArrayList<>(entries.size());
			for (GlobalPos globalPos : new ArrayList<>(entries.keySet())) {
				Entry entry = entries.get(globalPos);
				if (entry != null) {
					treeSnapshot.add(new DailyTreeEntry(
							globalPos,
							entry.treeId));
				}
			}
			long worldSeed = level.getSeed();
			int absoluteDay = context.absoluteDay();
			return DailySettlementWorkUnits.cursor(
					"wild_tree_seed",
					treeSnapshot,
					WildTreeSeedManager::dailyItemIdentity,
					entry -> processTreeDay(level, entry, worldSeed, absoluteDay),
					this::finishDailyProcessing);
		} catch (RuntimeException | Error exception) {
			finishDailyProcessing();
			throw exception;
		}
	}

	@SuppressWarnings("null")
	private void processTreeDay(
			ServerLevel level,
			DailyTreeEntry snapshot,
			long worldSeed,
			int absoluteDay) {
		GlobalPos globalPos = snapshot.globalPos();
		BlockPos pos = globalPos.pos();
		RandomSource random = DailySettlementRandom.forPosition(
				worldSeed, absoluteDay, "wild_tree_seed", pos);
		if (globalPos.dimension() != level.dimension()
				|| pendingRemoves.contains(globalPos)
				|| !com.stardew.craft.farm.FarmDailyProcessHelper.shouldProcessPosition(level, pos)) {
			return;
		}

		try (var lease = com.stardew.craft.farm.FarmDailyProcessHelper
				.leasePosition(level, pos, 8)) {
			if (!level.isLoaded(pos)) {
				return;
			}
			Entry liveEntry = entries.get(globalPos);
			if (liveEntry == null || !snapshot.expectedTreeId().equals(liveEntry.treeId)) {
				return;
			}
			WildTrees.Def def = findDefById(snapshot.expectedTreeId());
			if (def == null) {
				pendingRemoves.add(globalPos);
				setDirty();
				return;
			}
			BlockState treeState = level.getBlockState(pos);
			if (treeState.getBlock() != def.trunk0().get() && !def.isModernRoot(treeState)) {
				pendingRemoves.add(globalPos);
				setDirty();
				return;
			}
			if (def.isModernRoot(treeState)) {
				tryMigrateGeneratedTreeMarker(level, pos, def);
			}
			if (!isFullTree(level, pos, def)) {
				return;
			}

			WildSeedDailyState liveState = new WildSeedDailyState(
					liveEntry.hasSeed,
					liveEntry.lastSeedRollAbsDay,
					liveEntry.lastShakenAbsDay);
			WildSeedDailyState settledState = FarmDailyDecisions.reconcileWildSeedState(
					liveEntry.hasSeed,
					liveEntry.lastSeedRollAbsDay,
					liveEntry.lastShakenAbsDay,
					absoluteDay,
					random,
					seedOnShakeChance(def));
			if (!settledState.equals(liveState)) {
				liveEntry.hasSeed = settledState.hasSeed();
				liveEntry.lastSeedRollAbsDay = settledState.lastSeedRollAbsDay();
				liveEntry.lastShakenAbsDay = settledState.lastShakenAbsDay();
				setDirty();
			}
			if (FarmDailyDecisions.rollWildSpread(random, seedSpreadChance(def))) {
				BlockPos target = pos.offset(
						FarmDailyDecisions.rollWildOffset(random),
						0,
						FarmDailyDecisions.rollWildOffset(random));
				if (tryPlaceSapling(level, target, def)) {
					TreeGrowthManager.get(level).addSapling(level, target);
					setDirty();
				}
			}
		}
	}

	private void finishDailyProcessing() {
		processing = false;
		applyPendingChanges();
	}

	private void applyPendingChanges() {
		boolean changed = false;
		for (GlobalPos globalPos : pendingRemoves) {
			changed |= entries.remove(globalPos) != null;
		}
		pendingRemoves.clear();
		for (Map.Entry<GlobalPos, Entry> pendingAdd : pendingAdds.entrySet()) {
			changed |= entries.putIfAbsent(pendingAdd.getKey(), pendingAdd.getValue()) == null;
		}
		pendingAdds.clear();
		if (changed) {
			setDirty();
		}
	}

	private static String dailyItemIdentity(DailyTreeEntry entry) {
		GlobalPos globalPos = entry.globalPos();
		return globalPos.dimension().location() + ":" + globalPos.pos().toShortString();
	}

	private static DailySettlementContext contextForAbsoluteDay(int absoluteDay) {
		if (absoluteDay < 1) {
			throw new IllegalArgumentException("absoluteDay must be at least 1");
		}
		DailySettlementContext current = DailySettlementContextFactory.captureCurrentDay(
				StardewTimeManager.get());
		int zeroBasedDay = absoluteDay - 1;
		int year = zeroBasedDay / 112 + 1;
		int dayOfYear = zeroBasedDay % 112;
		int season = dayOfYear / 28;
		int day = dayOfYear % 28 + 1;
		return new DailySettlementContext(
				absoluteDay,
				year,
				season,
				day,
				current.sleepMinute(),
				current.seasonChanged(),
				current.playerIds(),
				current.farmOwnerIds());
	}

	private static boolean isFullTree(ServerLevel level, BlockPos trunk0Pos, WildTrees.Def def) {
		@SuppressWarnings("null")
		BlockState state = level.getBlockState(trunk0Pos);
		if (def.isModernRoot(state)) {
			return WildTrees.isModernCompleteTree(level, trunk0Pos, def);
		}
		BlockState above = level.getBlockState(trunk0Pos.above());
		return state.getBlock() == def.trunk0().get() && above.getBlock() == def.trunk1().get();
	}

	private static boolean tryPlaceSapling(ServerLevel level, BlockPos saplingPos, WildTrees.Def def) {
		@SuppressWarnings("null")
		BlockState at = level.getBlockState(saplingPos);
		if (!at.canBeReplaced()) {
			return false;
		}

		BlockPos groundPos = saplingPos.below();
		@SuppressWarnings("null")
		BlockState ground = level.getBlockState(groundPos);
		if (!isPlantableGround(ground)) {
			return false;
		}

		BlockState saplingState = def.sapling0().get().defaultBlockState();
		if (!saplingState.canSurvive(level, saplingPos)) {
			return false;
		}
		level.setBlock(saplingPos, saplingState, Block.UPDATE_ALL);
		return true;
	}

	@SuppressWarnings("null")
	private static boolean isPlantableGround(BlockState state) {
		if (state.getBlock() instanceof FarmBlock) {
			return false;
		}
		return state.is(net.minecraft.tags.BlockTags.DIRT) || state.is(net.minecraft.world.level.block.Blocks.GRASS_BLOCK);
	}

	private static float seedOnShakeChance(WildTrees.Def def) {
		return def.seedOnShakeChance();
	}

	private static float seedSpreadChance(WildTrees.Def def) {
		return def.seedSpreadChance();
	}

	private static void ensureRolledForDay(ServerLevel level, BlockPos trunk0Pos, WildTrees.Def def, Entry entry, int absDay) {
		if (entry.lastSeedRollAbsDay == absDay) {
			return;
		}
		// 如果没走到换日结算（例如新加入的树），则在首次交互时补一次当天 roll。
		entry.lastSeedRollAbsDay = absDay;
		entry.hasSeed = level.random.nextFloat() < seedOnShakeChance(def);
	}

	public static Item getSeedItem(WildTrees.Def def) {
		return switch (def.id()) {
			case "oak" -> ModItems.ACORN.get();
			case "maple" -> ModItems.MAPLE_SEED.get();
			case "pine" -> ModItems.PINE_CONE.get();
			case "mahogany" -> ModItems.MAHOGANY_SEED.get();
			case "mystic_tree" -> null;
			default -> null;
		};
	}

	private static Item getShakeDropItem(WildTrees.Def def) {
		if (isLateFallMaple(def)) {
			var hazelnut = ModItems.VANILLA_CATEGORY_ITEMS.get("hazelnut");
			return hazelnut != null ? hazelnut.get() : null;
		}
		return getSeedItem(def);
	}

	private static boolean isLateFallMaple(WildTrees.Def def) {
		StardewTimeManager time = StardewTimeManager.get();
		return "maple".equals(def.id())
				&& time.getCurrentSeason() == SEASON_FALL
				&& time.getCurrentDay() >= 14;
	}

	private static WildTrees.Def findDefById(String id) {
		for (WildTrees.Def def : WildTrees.ALL) {
			if (def.id().equals(id)) {
				return def;
			}
		}
		return null;
	}

	private static int getAbsDay() {
		StardewTimeManager time = StardewTimeManager.get();
		int year = time.getCurrentYear();
		int season = time.getCurrentSeason();
		int day = time.getCurrentDay();
		return (year - 1) * (28 * 4) + season * 28 + day;
	}

	private record DailyTreeEntry(
			GlobalPos globalPos,
			String expectedTreeId) {
	}

	@SuppressWarnings("null")
	@Override
	public CompoundTag save(@SuppressWarnings("null") CompoundTag tag, @SuppressWarnings("null") net.minecraft.core.HolderLookup.Provider provider) {
		ListTag list = new ListTag();
		for (Map.Entry<GlobalPos, Entry> e : entries.entrySet()) {
			CompoundTag t = new CompoundTag();
			GlobalPos gp = e.getKey();
			t.putString("Dimension", gp.dimension().location().toString());
			t.put("Pos", NbtUtils.writeBlockPos(gp.pos()));
			Entry v = e.getValue();
			t.putString("TreeId", v.treeId);
			t.putBoolean("HasSeed", v.hasSeed);
			t.putInt("LastSeedRoll", v.lastSeedRollAbsDay);
			t.putInt("LastShaken", v.lastShakenAbsDay);
			list.add(t);
		}
		tag.put("Trees", list);
		return tag;
	}

	public static WildTreeSeedManager load(CompoundTag tag, net.minecraft.core.HolderLookup.Provider provider) {
		WildTreeSeedManager data = new WildTreeSeedManager();
		ListTag list = tag.getList("Trees", Tag.TAG_COMPOUND);
		for (int i = 0; i < list.size(); i++) {
			CompoundTag t = list.getCompound(i);
			String dimStr = t.getString("Dimension");
			if (dimStr == null || dimStr.isBlank()) {
				continue;
			}
			@SuppressWarnings("null")
			ResourceKey<net.minecraft.world.level.Level> dim = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(dimStr));
			BlockPos pos = NbtUtils.readBlockPos(t, "Pos").orElse(BlockPos.ZERO);
			@SuppressWarnings("null")
			GlobalPos gp = GlobalPos.of(dim, pos);
			String treeId = t.getString("TreeId");
			Entry entry = new Entry(treeId);
			entry.hasSeed = t.getBoolean("HasSeed");
			entry.lastSeedRollAbsDay = t.getInt("LastSeedRoll");
			entry.lastShakenAbsDay = t.getInt("LastShaken");
			data.entries.put(gp, entry);
		}
		return data;
	}
}
