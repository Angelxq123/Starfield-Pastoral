package com.stardew.craft.manager;

import com.stardew.craft.block.tree.fruit.FruitTreeBlock;
import com.stardew.craft.block.tree.fruit.FruitTreeSaplingBlock;
import com.stardew.craft.blockentity.FruitTreeBlockEntity;
import com.stardew.craft.time.StardewTimeManager;
import com.stardew.craft.time.settlement.DailySettlementContext;
import com.stardew.craft.time.settlement.DailySettlementContextFactory;
import com.stardew.craft.time.settlement.DailySettlementWorkUnit;
import com.stardew.craft.time.settlement.DailySettlementWorkUnits;
import com.stardew.craft.tree.fruit.FruitTreeRules;
import com.stardew.craft.tree.fruit.FruitTreeType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class FruitTreeGrowthManager extends SavedData {
    private static final String DATA_NAME = "stardew_fruit_tree_manager";

    private final Map<GlobalPos, SaplingEntry> saplings = new ConcurrentHashMap<>();
    private final Set<GlobalPos> matureTrees = ConcurrentHashMap.newKeySet();
    private boolean processing;
    private final Set<GlobalPos> pendingSaplingRemoves = new HashSet<>();
    private final Set<GlobalPos> pendingMatureRemoves = new HashSet<>();

    public void addSapling(@Nonnull ServerLevel level, @Nonnull BlockPos pos, @Nonnull FruitTreeType type) {
        GlobalPos globalPos = toGlobalPos(level, pos);
        if (processing) {
            pendingSaplingRemoves.remove(globalPos);
        }
        saplings.putIfAbsent(globalPos, new SaplingEntry(type, FruitTreeType.DAYS_TO_MATURE));
        setDirty();
    }

    public void removeSapling(@Nonnull ServerLevel level, @Nonnull BlockPos pos) {
        GlobalPos globalPos = toGlobalPos(level, pos);
        if (processing) {
            pendingSaplingRemoves.add(globalPos);
        } else if (saplings.remove(globalPos) != null) {
            setDirty();
        }
    }

    public void addMatureTree(@Nonnull ServerLevel level, @Nonnull BlockPos pos) {
        matureTrees.add(toGlobalPos(level, pos));
        setDirty();
    }

    public void removeMatureTree(@Nonnull ServerLevel level, @Nonnull BlockPos pos) {
        GlobalPos globalPos = toGlobalPos(level, pos);
        if (processing) {
            pendingMatureRemoves.add(globalPos);
        } else if (matureTrees.remove(globalPos)) {
            setDirty();
        }
    }

    public int getDaysGrown(@Nonnull ServerLevel level, @Nonnull BlockPos pos) {
        BlockPos lowerPos = resolveSaplingLowerPos(level, pos);
        SaplingEntry entry = saplings.get(toGlobalPos(level, lowerPos));
        int daysRemaining = entry == null ? FruitTreeType.DAYS_TO_MATURE : entry.daysRemaining;
        return Math.max(0, FruitTreeType.DAYS_TO_MATURE - daysRemaining);
    }

    public int getDaysRemaining(@Nonnull ServerLevel level, @Nonnull BlockPos pos) {
        BlockPos lowerPos = resolveSaplingLowerPos(level, pos);
        SaplingEntry entry = saplings.get(toGlobalPos(level, lowerPos));
        return entry == null ? FruitTreeType.DAYS_TO_MATURE : Math.max(0, entry.daysRemaining);
    }

    public int getGrowthStage(@Nonnull ServerLevel level, @Nonnull BlockPos pos) {
        BlockPos lowerPos = resolveSaplingLowerPos(level, pos);
        BlockState state = level.getBlockState(lowerPos);
        if (!(state.getBlock() instanceof FruitTreeSaplingBlock saplingBlock)) {
            return 0;
        }
        SaplingEntry entry = saplings.get(toGlobalPos(level, lowerPos));
        int daysRemaining = entry == null ? FruitTreeType.DAYS_TO_MATURE : entry.daysRemaining;
        return saplingBlock.getType().visualStageFromDaysRemaining(daysRemaining);
    }

    public boolean isBlockedNow(@Nonnull ServerLevel level, @Nonnull BlockPos pos) {
        BlockPos lowerPos = resolveSaplingLowerPos(level, pos);
        return FruitTreeRules.isGrowthBlocked(level, lowerPos);
    }

    public void growOneDay(@Nonnull ServerLevel level, @Nonnull BlockPos pos) {
        BlockPos lowerPos = resolveSaplingLowerPos(level, pos);
        BlockState state = level.getBlockState(lowerPos);
        if (state.getBlock() instanceof FruitTreeBlock) {
            addMatureTree(level, lowerPos);
            processMatureTreeDay(level, lowerPos);
            return;
        }
        if (!(state.getBlock() instanceof FruitTreeSaplingBlock saplingBlock)
                || state.getValue(FruitTreeSaplingBlock.HALF) != DoubleBlockHalf.LOWER) {
            removeSapling(level, lowerPos);
            return;
        }

        addSapling(level, lowerPos, saplingBlock.getType());
        SaplingEntry entry = saplings.get(toGlobalPos(level, lowerPos));
        if (entry != null) {
            processSaplingDay(level, lowerPos, entry);
        }
    }

    public void growDaily(ServerLevel level) {
        DailySettlementWorkUnits.drain(createDailyWorkUnit(
                level,
                DailySettlementContextFactory.captureCurrentDay(StardewTimeManager.get())));
    }

    public DailySettlementWorkUnit createDailyWorkUnit(
            ServerLevel level,
            DailySettlementContext context) {
        if (processing) {
            throw new IllegalStateException("Fruit tree daily work is already active");
        }
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(context, "context");
        processing = true;
        try {
            List<DailyTreeEntry> saplingSnapshot = new ArrayList<>(saplings.size());
            for (Map.Entry<GlobalPos, SaplingEntry> entry : new ArrayList<>(saplings.entrySet())) {
                SaplingEntry sapling = entry.getValue();
                saplingSnapshot.add(new DailyTreeEntry(
                        DailyTreeKind.SAPLING,
                        entry.getKey(),
                        sapling.type,
                        sapling.daysRemaining));
            }
            DailySettlementWorkUnit saplingWork = DailySettlementWorkUnits.cursor(
                    "fruit_tree_growth",
                    saplingSnapshot,
                    FruitTreeGrowthManager::dailyItemIdentity,
                    entry -> processRegisteredTreeDay(level, entry),
                    () -> {});
            DailySettlementWorkUnit matureWork = DailySettlementWorkUnits.deferred(
                    "fruit_tree_mature_phase",
                    () -> createMatureDailyWorkUnit(level));
            return DailySettlementWorkUnits.sequence(
                    "fruit_tree_daily",
                    List.of(saplingWork, matureWork),
                    this::finishDailyProcessing);
        } catch (RuntimeException | Error exception) {
            finishDailyProcessing();
            throw exception;
        }
    }

    private DailySettlementWorkUnit createMatureDailyWorkUnit(ServerLevel level) {
        List<DailyTreeEntry> matureSnapshot = new ArrayList<>(matureTrees.size());
        for (GlobalPos globalPos : new ArrayList<>(matureTrees)) {
            matureSnapshot.add(new DailyTreeEntry(
                    DailyTreeKind.MATURE, globalPos, null, 0));
        }
        return DailySettlementWorkUnits.cursor(
                "fruit_tree_mature_growth",
                matureSnapshot,
                FruitTreeGrowthManager::dailyItemIdentity,
                entry -> processRegisteredTreeDay(level, entry),
                () -> {});
    }

    private void processRegisteredTreeDay(ServerLevel level, DailyTreeEntry entry) {
        GlobalPos globalPos = entry.globalPos();
        if (globalPos.dimension() != level.dimension()) {
            return;
        }
        BlockPos pos = globalPos.pos();
        if (!com.stardew.craft.farm.FarmDailyProcessHelper.shouldProcessPosition(level, pos)) {
            return;
        }
        if (!level.isLoaded(pos)) {
            return;
        }
        BlockState state = level.getBlockState(pos);
        if (entry.kind() == DailyTreeKind.SAPLING) {
            if (!(state.getBlock() instanceof FruitTreeSaplingBlock)
                    || state.getValue(FruitTreeSaplingBlock.HALF) != DoubleBlockHalf.LOWER) {
                removeSapling(level, pos);
                return;
            }
            SaplingEntry frozenSapling = new SaplingEntry(entry.type(), entry.daysRemaining());
            saplings.put(globalPos, frozenSapling);
            processSaplingDay(level, pos, frozenSapling);
            return;
        }
        if (entry.kind() == DailyTreeKind.MATURE) {
            if (!(state.getBlock() instanceof FruitTreeBlock)) {
                removeMatureTree(level, pos);
                return;
            }
            processMatureTreeDay(level, pos);
        }
    }

    private void finishDailyProcessing() {
        processing = false;
        applyPendingRemoves();
    }

    private static String dailyItemIdentity(DailyTreeEntry entry) {
        Objects.requireNonNull(entry, "entry");
        GlobalPos globalPos = entry.globalPos();
        return entry.kind() + ":" + globalPos.dimension().location()
                + ":" + globalPos.pos().toShortString();
    }

    public boolean strikeRandomMatureTree(@Nonnull ServerLevel level, @Nonnull RandomSource random) {
        java.util.List<BlockPos> candidates = new java.util.ArrayList<>();
        for (GlobalPos globalPos : new java.util.ArrayList<>(matureTrees)) {
            if (globalPos.dimension() != level.dimension()) {
                continue;
            }
            BlockPos pos = globalPos.pos();
            if (!level.isLoaded(pos)) {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof FruitTreeBlock)) {
                removeMatureTree(level, pos);
                continue;
            }
            if (level.getBlockEntity(pos) instanceof FruitTreeBlockEntity) {
                candidates.add(pos);
            }
        }
        if (candidates.isEmpty()) {
            return false;
        }

        BlockPos pos = candidates.get(random.nextInt(candidates.size()));
        if (level.getBlockEntity(pos) instanceof FruitTreeBlockEntity tree) {
            tree.strikeByLightning(level, pos);
            return true;
        }
        return false;
    }

    private void processSaplingDay(ServerLevel level, BlockPos pos, SaplingEntry entry) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof FruitTreeSaplingBlock saplingBlock)
                || state.getValue(FruitTreeSaplingBlock.HALF) != DoubleBlockHalf.LOWER) {
            removeSapling(level, pos);
            return;
        }

        FruitTreeType type = saplingBlock.getType();
        if (entry.daysRemaining <= 0) {
            matureSapling(level, pos, type);
            return;
        }

        if (FruitTreeRules.isGrowthBlocked(level, pos)) {
            updateVisualStage(level, pos, state, type.visualStageFromDaysRemaining(entry.daysRemaining));
            return;
        }

        entry.daysRemaining--;
        updateVisualStage(level, pos, state, type.visualStageFromDaysRemaining(entry.daysRemaining));
        if (entry.daysRemaining <= 0) {
            matureSapling(level, pos, type);
        }
        setDirty();
    }

    private void processMatureTreeDay(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof FruitTreeBlock)) {
            removeMatureTree(level, pos);
            return;
        }
        FruitTreeBlock.ensureExtensions(level, pos);
        if (level.getBlockEntity(pos) instanceof FruitTreeBlockEntity tree) {
            tree.dailyUpdate(level, pos);
        }
    }

    private void updateVisualStage(ServerLevel level, BlockPos pos, BlockState lowerState, int visualStage) {
        int currentStage = lowerState.getValue(FruitTreeSaplingBlock.AGE);
        if (currentStage == visualStage) {
            return;
        }
        BlockState nextLower = lowerState.setValue(FruitTreeSaplingBlock.AGE, visualStage)
                .setValue(FruitTreeSaplingBlock.HALF, DoubleBlockHalf.LOWER);
        level.setBlock(pos, nextLower, Block.UPDATE_ALL);
        BlockPos above = pos.above();
        if (level.getBlockState(above).getBlock() == lowerState.getBlock()) {
            level.setBlock(above, nextLower.setValue(FruitTreeSaplingBlock.HALF, DoubleBlockHalf.UPPER), Block.UPDATE_ALL);
        }
    }

    private void matureSapling(ServerLevel level, BlockPos pos, FruitTreeType type) {
        level.setBlock(pos.above(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(pos, type.matureBlock().defaultBlockState(), Block.UPDATE_ALL);
        if (level.getBlockEntity(pos) instanceof FruitTreeBlockEntity tree) {
            tree.setNewlyMature(type);
        }
        removeSapling(level, pos);
        addMatureTree(level, pos);
    }

    private void applyPendingRemoves() {
        boolean changed = false;
        for (GlobalPos globalPos : pendingSaplingRemoves) {
            changed |= saplings.remove(globalPos) != null;
        }
        pendingSaplingRemoves.clear();
        for (GlobalPos globalPos : pendingMatureRemoves) {
            changed |= matureTrees.remove(globalPos);
        }
        pendingMatureRemoves.clear();
        if (changed) {
            setDirty();
        }
    }

    private static GlobalPos toGlobalPos(Level level, BlockPos pos) {
        return GlobalPos.of(
                Objects.requireNonNull(level.dimension(), "dimension"),
                Objects.requireNonNull(pos.immutable(), "pos"));
    }

    private static BlockPos resolveSaplingLowerPos(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof FruitTreeSaplingBlock
                && state.getValue(FruitTreeSaplingBlock.HALF) == DoubleBlockHalf.UPPER) {
            return pos.below();
        }
        return pos;
    }

    @Override
    public @Nonnull CompoundTag save(@Nonnull CompoundTag tag, @Nonnull net.minecraft.core.HolderLookup.Provider provider) {
        ListTag saplingList = new ListTag();
        for (Map.Entry<GlobalPos, SaplingEntry> mapEntry : saplings.entrySet()) {
            CompoundTag entryTag = writeGlobalPos(mapEntry.getKey());
            entryTag.putString("Type", mapEntry.getValue().type.id());
            entryTag.putInt("DaysRemaining", mapEntry.getValue().daysRemaining);
            saplingList.add(entryTag);
        }
        tag.put("Saplings", saplingList);

        ListTag matureList = new ListTag();
        for (GlobalPos globalPos : matureTrees) {
            matureList.add(writeGlobalPos(globalPos));
        }
        tag.put("MatureTrees", matureList);
        return tag;
    }

    public static FruitTreeGrowthManager load(@Nonnull CompoundTag tag, @Nonnull net.minecraft.core.HolderLookup.Provider provider) {
        FruitTreeGrowthManager manager = new FruitTreeGrowthManager();
        if (tag.contains("Saplings", Tag.TAG_LIST)) {
            ListTag list = tag.getList("Saplings", Tag.TAG_COMPOUND);
            for (int index = 0; index < list.size(); index++) {
                CompoundTag entryTag = list.getCompound(index);
                GlobalPos globalPos = readGlobalPos(entryTag);
                if (globalPos != null) {
                    FruitTreeType type = FruitTreeType.byId(entryTag.getString("Type"));
                    int days = Math.max(0, Math.min(FruitTreeType.DAYS_TO_MATURE, entryTag.getInt("DaysRemaining")));
                    manager.saplings.put(globalPos, new SaplingEntry(type, days));
                }
            }
        }
        if (tag.contains("MatureTrees", Tag.TAG_LIST)) {
            ListTag list = tag.getList("MatureTrees", Tag.TAG_COMPOUND);
            for (int index = 0; index < list.size(); index++) {
                GlobalPos globalPos = readGlobalPos(list.getCompound(index));
                if (globalPos != null) {
                    manager.matureTrees.add(globalPos);
                }
            }
        }
        return manager;
    }

    public static FruitTreeGrowthManager get(ServerLevel level) {
        ServerLevel overworld = level.getServer().overworld();
        return overworld.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(FruitTreeGrowthManager::new, FruitTreeGrowthManager::load),
                DATA_NAME);
    }

    private static CompoundTag writeGlobalPos(GlobalPos globalPos) {
        CompoundTag tag = new CompoundTag();
        tag.putString("Dimension", globalPos.dimension().location().toString());
        tag.put("Pos", NbtUtils.writeBlockPos(globalPos.pos()));
        return tag;
    }

    private static GlobalPos readGlobalPos(CompoundTag tag) {
        if (!tag.contains("Dimension", Tag.TAG_STRING) || !tag.contains("Pos", Tag.TAG_COMPOUND)) {
            return null;
        }
        ResourceKey<Level> dimension = ResourceKey.create(
                net.minecraft.core.registries.Registries.DIMENSION,
                net.minecraft.resources.ResourceLocation.parse(tag.getString("Dimension")));
        BlockPos pos = NbtUtils.readBlockPos(tag, "Pos").orElse(null);
        return pos == null ? null : GlobalPos.of(dimension, pos);
    }

    private static final class SaplingEntry {
        private final FruitTreeType type;
        private int daysRemaining;

        private SaplingEntry(FruitTreeType type, int daysRemaining) {
            this.type = type;
            this.daysRemaining = daysRemaining;
        }
    }

    private enum DailyTreeKind {
        SAPLING,
        MATURE
    }

    private record DailyTreeEntry(
            DailyTreeKind kind,
            GlobalPos globalPos,
            FruitTreeType type,
            int daysRemaining) {
        private DailyTreeEntry {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(globalPos, "globalPos");
            if (kind == DailyTreeKind.SAPLING) {
                Objects.requireNonNull(type, "type");
            }
        }
    }
}
