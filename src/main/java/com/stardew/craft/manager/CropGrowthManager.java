package com.stardew.craft.manager;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.api.v1.agriculture.StardewCropRuntimeAdapter;
import com.stardew.craft.api.v1.agriculture.StardewCropState;
import com.stardew.craft.api.v1.internal.crop.StardewCropRuntimeRegistry;
import com.stardew.craft.block.crop.StardewCropBlock;
import com.stardew.craft.manager.FertilizerManager;
import com.stardew.craft.time.StardewTimeManager;
import com.stardew.craft.time.settlement.DailySettlementContext;
import com.stardew.craft.time.settlement.DailySettlementContextFactory;
import com.stardew.craft.time.settlement.DailySettlementWorkUnit;
import com.stardew.craft.time.settlement.DailySettlementWorkUnits;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;

/**
 * 作物生长管理器
 * 记录所有星露谷作物的位置，并在每天换日时统一处理生长
 */
@SuppressWarnings("unused")
public class CropGrowthManager extends SavedData {
    private static final String DATA_NAME = "stardew_crop_manager";
    
    // 存储所有作物的位置 (使用GlobalPos以支持多维度，虽然目前只限星露谷维度)
    private final Set<GlobalPos> cropPositions = new HashSet<>();
    /**
     * 公共区域（非任何玩家农场）耕作过的区块（chunk key）集合。
     * 由 HoeItem 在锄成耕地后调用 {@link #trackPublicTilledChunk} 登记，
     * 用于次日 dryAllFarmland 时确保被扫描，避免"小镇耕地永远不复原"。
     * 扫描后保留集合（耕地可能被反复锄）；只有当过夜还原后该 chunk 内确无耕地残留时清理。
     */
    private final Set<Long> publicTilledChunks = new HashSet<>();

    /**
     * 每株作物的生长状态：当前阶段已过天数 + 是否处于“再生长倒计时”状态。
     * 这是实现 Stardew Valley 原版 Crop.newDay() 的关键状态。
     */
    private final Map<GlobalPos, CropGrowthState> cropStates = new ConcurrentHashMap<>();

    // 防止在遍历时被 onRemove/onPlace 修改导致 ConcurrentModificationException
    private boolean isProcessing = false;
    private com.stardew.craft.farm.FarmDailyProcessHelper.ReusingPositionLease activeDailyLease;
    private final Set<GlobalPos> pendingAdds = new HashSet<>();
    private final Set<GlobalPos> pendingRemoves = new HashSet<>();

    public CropGrowthManager() {}

    /** 返回所有已注册作物位置的不可变快照。 */
    public java.util.List<GlobalPos> getAllCropPositions() {
        return new java.util.ArrayList<>(cropPositions);
    }

    /** 获取或创建某个作物位置的生长状态。 */
    public CropGrowthState getOrCreateGrowthState(GlobalPos gp) {
        return cropStates.computeIfAbsent(gp, k -> new CropGrowthState());
    }

    public static class CropGrowthState {
        public int dayInPhase;
        /**
         * 当前处于哪一个“星露谷 phase”（0-3）。
         * 注意：我们的方块 AGE 只是 0-3 的渲染阶段，其中 AGE=3 需要只在成熟时出现，
         * 因此不能再用 AGE 直接当 phase。
         */
        public int phase;
        public boolean regrowing;
        public UUID planterUuid;

        public CropGrowthState() {
            this(0, 0, false, null);
        }

        public CropGrowthState(int dayInPhase, int phase, boolean regrowing, UUID planterUuid) {
            this.dayInPhase = dayInPhase;
            this.phase = phase;
            this.regrowing = regrowing;
            this.planterUuid = planterUuid;
        }
    }

    public CropGrowthState getOrCreateState(Level level, BlockPos pos) {
        @SuppressWarnings("null")
        GlobalPos globalPos = GlobalPos.of(level.dimension(), pos.immutable());
        return cropStates.computeIfAbsent(globalPos, (k) -> new CropGrowthState());
    }

    /**
     * 获取作物生长状态（不会创建新状态）。
     * 若未记录，则返回 null。
     */
    public CropGrowthState getState(Level level, BlockPos pos) {
        @SuppressWarnings("null")
        GlobalPos globalPos = GlobalPos.of(level.dimension(), pos.immutable());
        return cropStates.get(globalPos);
    }

    public void setRegrowing(Level level, BlockPos pos, boolean regrowing, int dayInPhase, int phase) {
        if (!(level instanceof ServerLevel)) {
            return;
        }
        CropGrowthState state = getOrCreateState(level, pos);
        state.regrowing = regrowing;
        state.dayInPhase = Math.max(0, dayInPhase);
        state.phase = Math.max(0, phase);
        setDirty();
    }

    /**
     * 登记一个"公共区域（小镇/沙漠等非任何农场）"被锄成耕地的位置所在区块，
     * 用于次日 dryAllFarmland 时强制扫描。HoeItem 在 tillTile 成功后调用。
     */
    public void trackPublicTilledChunk(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel)) return;
        long key = net.minecraft.world.level.ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4);
        if (publicTilledChunks.add(key)) {
            setDirty();
        }
    }

    /**
     * 添加作物位置
     */
    public void addCrop(Level level, BlockPos pos) {
        addCrop(level, pos, null);
    }

    /**
     * 添加作物位置并记录最近种植者，用于职业判定（如 Agriculturist）。
     */
    public void addCrop(Level level, BlockPos pos, UUID planterUuid) {
        if (level instanceof ServerLevel) {
            @SuppressWarnings("null")
            GlobalPos globalPos = GlobalPos.of(level.dimension(), pos.immutable());
            if (isProcessing) {
                pendingAdds.add(globalPos);
                pendingRemoves.remove(globalPos);
                CropGrowthState state = cropStates.computeIfAbsent(globalPos, k -> new CropGrowthState());
                if (planterUuid != null && state.planterUuid == null) {
                    state.planterUuid = planterUuid;
                }
                setDirty();
                return;
            }
            if (cropPositions.add(globalPos)) {
                CropGrowthState state = cropStates.computeIfAbsent(globalPos, k -> new CropGrowthState());
                if (planterUuid != null && state.planterUuid == null) {
                    state.planterUuid = planterUuid;
                }
                setDirty();
                return;
            }

            CropGrowthState state = cropStates.computeIfAbsent(globalPos, k -> new CropGrowthState());
            if (planterUuid != null && state.planterUuid == null) {
                state.planterUuid = planterUuid;
                setDirty();
            }
        }
    }

    /**
     * 移除作物位置
     */
    public void removeCrop(Level level, BlockPos pos) {
        if (level instanceof ServerLevel) {
            @SuppressWarnings("null")
            GlobalPos globalPos = GlobalPos.of(level.dimension(), pos.immutable());
            if (isProcessing) {
                pendingRemoves.add(globalPos);
                pendingAdds.remove(globalPos);
                cropStates.remove(globalPos);
                setDirty();
                return;
            }
            if (cropPositions.remove(globalPos)) {
                cropStates.remove(globalPos);
                setDirty();
            }
        }
    }

    private void applyPendingChanges() {
        boolean changed = false;
        if (!pendingRemoves.isEmpty()) {
            changed |= cropPositions.removeAll(pendingRemoves);
            for (GlobalPos p : pendingRemoves) {
                cropStates.remove(p);
            }
            pendingRemoves.clear();
        }
        if (!pendingAdds.isEmpty()) {
            changed |= cropPositions.addAll(pendingAdds);
            for (GlobalPos p : pendingAdds) {
                cropStates.putIfAbsent(p, new CropGrowthState());
            }
            pendingAdds.clear();
        }
        if (changed) {
            setDirty();
        }
    }

    /**
     * 每日生长结算
     * 由 TimeManager 在 advanceDay() 时调用
     */
    @SuppressWarnings("null")
    public void growDaily(ServerLevel level) {
        DailySettlementWorkUnits.drain(createDailyWorkUnit(
                level,
                DailySettlementContextFactory.captureCurrentDay(StardewTimeManager.get())));
    }

    public DailySettlementWorkUnit createDailyWorkUnit(
            ServerLevel level,
            DailySettlementContext context) {
        if (isProcessing) {
            throw new IllegalStateException("Crop daily work is already active");
        }
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(context, "context");
        isProcessing = true;
        try {
            activeDailyLease = com.stardew.craft.farm.FarmDailyProcessHelper
                    .reusingPositionLease(level, 1);
            List<GlobalPos> snapshot = new java.util.ArrayList<>(cropPositions);
            snapshot.sort(com.stardew.craft.farm.FarmDailyProcessHelper
                    .globalPositionLeaseOrder(1));
            DailySettlementWorkUnit cropEntries = DailySettlementWorkUnits.cursor(
                    "crop_growth",
                    snapshot,
                    CropGrowthManager::dailyItemIdentity,
                    globalPos -> processCropDay(level, globalPos),
                    this::closeDailyLease);
            DailySettlementWorkUnit farmlandScan = createFarmlandScanWorkUnit(level);
            return DailySettlementWorkUnits.sequence(
                    "crop_daily",
                    List.of(cropEntries, farmlandScan),
                    this::finishDailyProcessing);
        } catch (RuntimeException | Error exception) {
            finishDailyProcessing();
            throw exception;
        }
    }

    @SuppressWarnings("null")
    private void processCropDay(ServerLevel level, GlobalPos globalPos) {
        if (globalPos.dimension() != level.dimension()) {
            return;
        }
        BlockPos pos = globalPos.pos();
        if (!com.stardew.craft.farm.FarmDailyProcessHelper.shouldProcessPosition(level, pos)) {
            return;
        }
        var leaseCursor = Objects.requireNonNull(
                activeDailyLease, "crop daily chunk lease");
        try (var lease = leaseCursor.lease(pos)) {
            if (!level.isLoaded(pos)) {
                return;
            }

            BlockState state = level.getBlockState(pos);
            Block block = state.getBlock();
            boolean coreCrop = block instanceof StardewCropBlock;
            StardewCropState runtimeCrop = coreCrop
                    ? StardewCropRuntimeRegistry.inspect(level, pos)
                    : StardewCropRuntimeRegistry.inspectAddon(level, pos);
            if (runtimeCrop == null) {
                removeCrop(level, pos);
                return;
            }

            boolean isWatered = runtimeCrop.soilPositions().stream()
                    .map(level::getBlockState)
                    .anyMatch(soil -> soil.getBlock() instanceof FarmBlock
                            && soil.getValue(FarmBlock.MOISTURE) > 0);
            StardewCropRuntimeAdapter.DailyResult result =
                    StardewCropRuntimeRegistry.growOneDay(
                            level, pos, isWatered, false);
            setDirty();
            if (result == StardewCropRuntimeAdapter.DailyResult.REMOVED) {
                removeCrop(level, pos);
                return;
            }

            BlockState afterGrow = level.getBlockState(pos);
            if (afterGrow.getBlock() instanceof StardewCropBlock matureCheck
                    && afterGrow.hasProperty(StardewCropBlock.AGE)
                    && afterGrow.getValue(StardewCropBlock.AGE) == StardewCropBlock.MAX_AGE) {
                com.stardew.craft.spawner.GiantCropSpawner.tryRoll(level, pos, matureCheck);
            }
        }
    }

    private void finishDailyProcessing() {
        try {
            closeDailyLease();
        } finally {
            isProcessing = false;
            applyPendingChanges();
        }
    }

    private void closeDailyLease() {
        var lease = activeDailyLease;
        activeDailyLease = null;
        if (lease != null) {
            lease.close();
        }
    }

    private static String dailyItemIdentity(GlobalPos globalPos) {
        Objects.requireNonNull(globalPos, "globalPos");
        return globalPos.dimension().location() + ":" + globalPos.pos().toShortString();
    }

    /**
     * 立即检查并枯萎所有“已加载区块里”的非当季作物。
     * 用于调试命令改季节后立刻生效，避免等到第二天。
     */
    @SuppressWarnings("null")
    public void killOutOfSeasonLoaded(ServerLevel serverLevel) {
        isProcessing = true;
        try {
            // 使用快照遍历，避免 setBlock 触发 add/remove 导致 HashSet 迭代器 CME
            java.util.List<GlobalPos> snapshot = new java.util.ArrayList<>(cropPositions);
            for (GlobalPos globalPos : snapshot) {

                if (globalPos.dimension() != serverLevel.dimension()) {
                    continue;
                }

                BlockPos pos = globalPos.pos();
                if (!serverLevel.isLoaded(pos)) {
                    continue;
                }

                @SuppressWarnings("null")
                BlockState state = serverLevel.getBlockState(pos);
                Block block = state.getBlock();
                if (block instanceof StardewCropBlock cropBlock) {
                    CropGrowthState growthState = cropStates.computeIfAbsent(globalPos, (k) -> new CropGrowthState());
                    // 传 watered=false，确保不会推进生长，但仍会触发“不在季节 -> 枯萎”替换。
                    cropBlock.growCropOneDay(serverLevel, pos, state, false, growthState);
                    // growthState is mutated in-place
                    setDirty();
                } else {
                    removeCrop(serverLevel, pos);
                }
            }
        } finally {
            isProcessing = false;
            applyPendingChanges();
        }
    }
    
    private DailySettlementWorkUnit createFarmlandScanWorkUnit(ServerLevel level) {
        java.util.List<Long> chunkSnapshot = new java.util.ArrayList<>(
                collectFarmlandChunkKeys(level));
        return DailySettlementWorkUnits.cursor(
                "farmland_scan",
                chunkSnapshot,
                key -> Long.toString(key),
                key -> dryFarmlandChunk(level, key),
                () -> {});
    }

    @SuppressWarnings("null")
    private java.util.Set<Long> collectFarmlandChunkKeys(ServerLevel level) {
        // Keep the existing target set: active farms, registered crops, and public tilled chunks.
        java.util.Set<Long> chunkKeys = new java.util.HashSet<>();
        com.stardew.craft.farm.FarmInstanceRegistry farmReg =
                com.stardew.craft.farm.FarmInstanceRegistry.get();
        for (net.minecraft.server.level.ServerPlayer player : level.players()) {
            com.stardew.craft.farm.FarmInstance farm = farmReg.getFarmForPlayer(player.getUUID());
            if (farm == null) {
                continue;
            }
            BlockPos min = farm.getFarmBoundsMin();
            BlockPos max = farm.getFarmBoundsMax();
            int minCX = min.getX() >> 4;
            int maxCX = max.getX() >> 4;
            int minCZ = min.getZ() >> 4;
            int maxCZ = max.getZ() >> 4;
            for (int cx = minCX; cx <= maxCX; cx++) {
                for (int cz = minCZ; cz <= maxCZ; cz++) {
                    chunkKeys.add(net.minecraft.world.level.ChunkPos.asLong(cx, cz));
                }
            }
        }
        for (GlobalPos gp : cropPositions) {
            if (gp.dimension() != level.dimension()) {
                continue;
            }
            BlockPos pos = gp.pos();
            chunkKeys.add(net.minecraft.world.level.ChunkPos.asLong(
                    pos.getX() >> 4, pos.getZ() >> 4));
        }
        chunkKeys.addAll(publicTilledChunks);
        return chunkKeys;
    }

    @SuppressWarnings("null")
    private void dryFarmlandChunk(ServerLevel level, long key) {
        FertilizerManager fertilizerManager = FertilizerManager.get(level);
        int cx = net.minecraft.world.level.ChunkPos.getX(key);
        int cz = net.minecraft.world.level.ChunkPos.getZ(key);
        net.minecraft.world.level.chunk.LevelChunk chunk =
                level.getChunkSource().getChunk(cx, cz, false);
        if (chunk == null) {
            return;
        }

        net.minecraft.world.level.chunk.LevelChunkSection[] sections = chunk.getSections();
        for (int i = 0; i < sections.length; i++) {
            net.minecraft.world.level.chunk.LevelChunkSection section = sections[i];
            if (section.hasOnlyAir()
                    || !section.getStates().maybeHas(
                            state -> state.getBlock()
                                    instanceof net.minecraft.world.level.block.FarmBlock)) {
                continue;
            }

            int bottomY = chunk.getSectionYFromSectionIndex(i) << 4;
            for (int x = 0; x < 16; x++) {
                for (int y = 0; y < 16; y++) {
                    for (int z = 0; z < 16; z++) {
                        BlockState state = section.getBlockState(x, y, z);
                        if (!(state.getBlock()
                                instanceof net.minecraft.world.level.block.FarmBlock)) {
                            continue;
                        }
                        BlockPos realPos = new BlockPos(
                                chunk.getPos().getMinBlockX() + x,
                                bottomY + y,
                                chunk.getPos().getMinBlockZ() + z);
                        boolean permanentContainer = state.getBlock()
                                instanceof com.stardew.craft.block.utility.GardenPotBlock;
                        boolean publicArea = com.stardew.craft.core.FarmAreaResolver
                                .isInStardewButNotFarm(level, realPos);
                        boolean greenhouse = com.stardew.craft.greenhouse.GreenhouseManager
                                .isInGreenhouseInterior(level, realPos);
                        if (!permanentContainer && publicArea && !greenhouse) {
                            BlockState above = level.getBlockState(realPos.above());
                            if (!isSoilProtectingBlock(above)) {
                                fertilizerManager.removeFertilizer(level, realPos);
                                level.setBlock(realPos,
                                        com.stardew.craft.block.ModBlocks.YELLOW_DIRT.get()
                                                .defaultBlockState(),
                                        Block.UPDATE_ALL);
                                continue;
                            }
                        }
                        if (!permanentContainer && !publicArea && !greenhouse) {
                            BlockState above = level.getBlockState(realPos.above());
                            if (!isSoilProtectingBlock(above)
                                    && level.random.nextFloat() < 0.1f) {
                                fertilizerManager.removeFertilizer(level, realPos);
                                level.setBlock(realPos,
                                        com.stardew.craft.block.ModBlocks.YELLOW_DIRT.get()
                                                .defaultBlockState(),
                                        Block.UPDATE_ALL);
                                continue;
                            }
                        }
                        int moisture = state.getValue(
                                net.minecraft.world.level.block.FarmBlock.MOISTURE);
                        if (moisture > 0) {
                            float retain = fertilizerManager.getWaterRetention(level, realPos);
                            if (retain > 0f && level.random.nextFloat() < retain) {
                                continue;
                            }
                            level.setBlock(realPos,
                                    state.setValue(
                                            net.minecraft.world.level.block.FarmBlock.MOISTURE, 0),
                                    2);
                        }
                    }
                }
            }
        }
    }

    /**
     * 判断「上方方块」是否能保护下方耕地不在过夜时回退为黄土。
     * 包括：
     *  - {@link StardewCropBlock}：所有自定义作物（含 WildSeedCropBlock）；
     *  - {@link com.stardew.craft.block.nature.ForageBlock}：X 季种成熟后变成的 forage 方块（蒲公英、雪人参等）。
     */
    private static boolean isSoilProtectingBlock(BlockState above) {
        Block block = above.getBlock();
        return block instanceof StardewCropBlock
            || block instanceof com.stardew.craft.block.nature.ForageBlock;
    }

    @SuppressWarnings("null")
    @Override
    public CompoundTag save(@SuppressWarnings("null") CompoundTag tag, @SuppressWarnings("null") net.minecraft.core.HolderLookup.Provider provider) {
        ListTag list = new ListTag();
        for (GlobalPos pos : cropPositions) {
            // GlobalPos 没有内置codec直接转tag的方法比较方便，我们手动存一下或者用NbtUtils存BlockPos
            CompoundTag posTag = new CompoundTag();
            posTag.putString("Dimension", pos.dimension().location().toString());
            posTag.put("Pos", NbtUtils.writeBlockPos(pos.pos()));

            CropGrowthState state = cropStates.get(pos);
            if (state != null) {
                posTag.putInt("DayInPhase", state.dayInPhase);
                posTag.putInt("Phase", state.phase);
                posTag.putBoolean("Regrowing", state.regrowing);
                if (state.planterUuid != null) {
                    posTag.putUUID("PlanterUuid", state.planterUuid);
                }
            }

            list.add(posTag);
        }
        tag.put("Crops", list);
        // 持久化公共区耕地区块（小镇/沙漠等非农场区域）
        long[] tilled = new long[publicTilledChunks.size()];
        int idx = 0;
        for (long key : publicTilledChunks) tilled[idx++] = key;
        tag.putLongArray("PublicTilledChunks", tilled);
        return tag;
    }

    public static CropGrowthManager load(CompoundTag tag, net.minecraft.core.HolderLookup.Provider provider) {
        CropGrowthManager manager = new CropGrowthManager();
        if (tag.contains("Crops", Tag.TAG_LIST)) {
            ListTag list = tag.getList("Crops", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag posTag = list.getCompound(i);
                @SuppressWarnings("null")
                ResourceKey<Level> dim = ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, 
                        net.minecraft.resources.ResourceLocation.parse(posTag.getString("Dimension")));
                BlockPos pos = NbtUtils.readBlockPos(posTag, "Pos").orElse(BlockPos.ZERO);
                @SuppressWarnings("null")
                GlobalPos gp = GlobalPos.of(dim, pos);
                manager.cropPositions.add(gp);

                int dayInPhase = posTag.contains("DayInPhase", Tag.TAG_INT) ? posTag.getInt("DayInPhase") : 0;
                int phase = posTag.contains("Phase", Tag.TAG_INT) ? posTag.getInt("Phase") : 0;
                boolean regrowing = posTag.contains("Regrowing", Tag.TAG_BYTE) && posTag.getBoolean("Regrowing");
                UUID planterUuid = posTag.hasUUID("PlanterUuid") ? posTag.getUUID("PlanterUuid") : null;
                manager.cropStates.put(gp, new CropGrowthState(dayInPhase, phase, regrowing, planterUuid));
            }
        }
        if (tag.contains("PublicTilledChunks", Tag.TAG_LONG_ARRAY)) {
            for (long key : tag.getLongArray("PublicTilledChunks")) {
                manager.publicTilledChunks.add(key);
            }
        }
        return manager;
    }

    @SuppressWarnings("null")
    public static CropGrowthManager get(ServerLevel level) {
        // 数据保存在主世界(Overworld)的存储中，全局共享
        ServerLevel overworld = level.getServer().overworld();
        return overworld.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(
                        CropGrowthManager::new,
                        CropGrowthManager::load,
                        null
                ),
                DATA_NAME
        );
    }
}
