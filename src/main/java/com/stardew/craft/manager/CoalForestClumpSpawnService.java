package com.stardew.craft.manager;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.decor.ResourceClumpBlock;
import com.stardew.craft.core.ModDimensions;
import com.stardew.craft.time.StardewTimeManager;
import com.stardew.craft.time.settlement.DailySettlementContext;
import com.stardew.craft.time.settlement.DailySettlementContextFactory;
import com.stardew.craft.time.settlement.DailySettlementRandom;
import com.stardew.craft.time.settlement.DailySettlementWorkUnit;
import com.stardew.craft.time.settlement.DailySettlementWorkUnits;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

@EventBusSubscriber(modid = StardewCraft.MODID)
public final class CoalForestClumpSpawnService {
    private static final String INIT_DATA_ID = "stardewcraft_coal_forest_clumps_init";
    private static final int CLEAR_MAX_Y = CoalForestArea.MAX_Y + 8;
    private static final List<BlockPos> LARGE_STUMP_POSITIONS = List.of(
            new BlockPos(-205, 68, 5),
            new BlockPos(-235, 68, 9),
            new BlockPos(-231, 68, 7),
            new BlockPos(-237, 68, 5),
            new BlockPos(-212, 68, 35),
            new BlockPos(-223, 68, 35));
    private static final int INITIAL_COLUMNS_PER_TICK = 64;
    private static InitialSpawnJob initialSpawnJob;

    private CoalForestClumpSpawnService() {
    }

    public static void onNewDay(ServerLevel level) {
        DailySettlementWorkUnits.drain(createDailyWorkUnit(
                level,
                DailySettlementContextFactory.captureCurrentDay(StardewTimeManager.get())));
    }

    public static DailySettlementWorkUnit createDailyWorkUnit(
            ServerLevel level,
            DailySettlementContext context) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(context, "context");
        if (!ModDimensions.STARDEW_VALLEY.equals(level.dimension())) {
            return DailySettlementWorkUnits.sequence("coal_forest_daily", List.of(), () -> {});
        }
        DailySettlementWorkUnit clear = PublicAreaDailyWorkUnits.rectangle(
                "coal_forest_clear",
                CoalForestArea.MIN_X,
                CoalForestArea.MIN_Z,
                CoalForestArea.MAX_X,
                CoalForestArea.MAX_Z,
                (x, z) -> processClearColumn(level, x, z),
                () -> false,
                () -> {});
        long worldSeed = level.getSeed();
        int absoluteDay = context.absoluteDay();
        AtomicInteger spawned = new AtomicInteger();
        DailySettlementWorkUnit attempts = DailySettlementWorkUnits.cursor(
                "coal_forest_stump_attempts",
                LARGE_STUMP_POSITIONS,
                pos -> "coal_forest_stump:" + pos.asLong(),
                pos -> processStumpAttempt(level, pos, worldSeed, absoluteDay, spawned),
                () -> StardewCraft.LOGGER.info(
                        "[SECRET_WOODS] Daily stump respawn: largeStump={}/{}",
                        spawned.get(), LARGE_STUMP_POSITIONS.size()));
        return DailySettlementWorkUnits.sequence(
                "coal_forest_daily", List.of(clear, attempts), () -> {});
    }

    private static void processClearColumn(ServerLevel level, int x, int z) {
        if (!PublicAreaDailyWorkUnits.isChunkLoadedNow(level, x, z)) return;
        for (int y = CoalForestArea.MIN_Y; y <= CLEAR_MAX_Y; y++) {
            BlockPos pos = new BlockPos(x, y, z);
            BlockState state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof ResourceClumpBlock)) continue;
            if (!state.hasProperty(com.stardew.craft.block.decor.MapDecorStaticBlock.PART)
                    || state.getValue(com.stardew.craft.block.decor.MapDecorStaticBlock.PART)
                    != com.stardew.craft.block.decor.MapDecorStaticBlock.Part.MAIN) continue;
            level.setBlock(
                    pos,
                    net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),
                    Block.UPDATE_ALL);
        }
    }

    private static void processStumpAttempt(
            ServerLevel level,
            BlockPos pos,
            long worldSeed,
            int absoluteDay,
            AtomicInteger spawned) {
        RandomSource random = DailySettlementRandom.forPosition(
                worldSeed, absoluteDay, "coal_forest_stump", pos);
        if (tryPlaceAt(level, random, ModBlocks.LARGE_STUMP.get(), pos)) {
            spawned.incrementAndGet();
        } else {
            StardewCraft.LOGGER.warn("[SECRET_WOODS] Failed to place large stump at {}", pos);
        }
    }

    public static void ensureInitialSpawn(ServerLevel level) {
        if (!ModDimensions.STARDEW_VALLEY.equals(level.dimension())) {
            return;
        }

        CoalForestClumpInitData data = level.getDataStorage().computeIfAbsent(
                CoalForestClumpInitData.factory(), INIT_DATA_ID);
        if (data.initialized()) {
            return;
        }

        if (initialSpawnJob == null) {
            initialSpawnJob = new InitialSpawnJob(level);
            StardewCraft.LOGGER.info(
                    "[SECRET_WOODS] Scheduled gradual initial stump spawn");
        }
    }

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || initialSpawnJob == null
                || initialSpawnJob.level != level) {
            return;
        }
        tickInitialSpawn(initialSpawnJob);
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        if (initialSpawnJob != null) {
            releaseInitialChunks(initialSpawnJob);
            initialSpawnJob = null;
        }
    }

    private static void tickInitialSpawn(InitialSpawnJob job) {
        if (job.cursor.hasChunkRequest()) {
            ChunkPos chunk = job.cursor.pollChunkRequest();
            job.requiredChunks.add(chunk);
            if (!job.level.getForcedChunks().contains(chunk.toLong())
                    && job.level.setChunkForced(chunk.x, chunk.z, true)) {
                job.ownedChunks.add(chunk);
            }
            return;
        }
        for (ChunkPos chunk : job.requiredChunks) {
            if (job.level.getChunkSource().getChunkNow(chunk.x, chunk.z) == null) {
                return;
            }
        }

        int processed = 0;
        while (processed < INITIAL_COLUMNS_PER_TICK && job.cursor.hasColumn()) {
            InitialRegionWorkCursor.Column column = job.cursor.currentColumn();
            processClearColumn(job.level, column.x(), column.z());
            job.cursor.advanceColumn();
            processed++;
        }
        if (job.cursor.hasColumn()) {
            return;
        }

        int spawned = 0;
        for (BlockPos pos : LARGE_STUMP_POSITIONS) {
            if (tryPlaceAt(job.level, job.random, ModBlocks.LARGE_STUMP.get(), pos)) {
                spawned++;
            } else {
                StardewCraft.LOGGER.warn(
                        "[SECRET_WOODS] Failed to place large stump at {}", pos);
            }
        }
        if (initialSpawnComplete(spawned)) {
            CoalForestClumpInitData data = job.level.getDataStorage().computeIfAbsent(
                    CoalForestClumpInitData.factory(), INIT_DATA_ID);
            data.setInitialized(true);
        }
        StardewCraft.LOGGER.info("[SECRET_WOODS] Gradual initial stump spawn: largeStump={}/{}",
                spawned, LARGE_STUMP_POSITIONS.size());
        releaseInitialChunks(job);
        initialSpawnJob = null;
    }

    private static boolean initialSpawnComplete(int spawned) {
        return spawned > 0;
    }

    private static void releaseInitialChunks(InitialSpawnJob job) {
        for (ChunkPos chunk : job.ownedChunks) {
            job.level.setChunkForced(chunk.x, chunk.z, false);
        }
        job.ownedChunks.clear();
    }

    private static final class InitialSpawnJob {
        private final ServerLevel level;
        private final InitialRegionWorkCursor cursor = new InitialRegionWorkCursor(
                CoalForestArea.MIN_X,
                CoalForestArea.MAX_X,
                CoalForestArea.MIN_Z,
                CoalForestArea.MAX_Z);
        private final List<ChunkPos> requiredChunks = new ArrayList<>();
        private final List<ChunkPos> ownedChunks = new ArrayList<>();
        private final RandomSource random;

        private InitialSpawnJob(ServerLevel level) {
            this.level = level;
            this.random = level.getRandom();
        }
    }

    private static boolean tryPlaceAt(ServerLevel level, RandomSource random, Block block, BlockPos mainPos) {
        if (!(block instanceof ResourceClumpBlock clump)) {
            return false;
        }

        if (!CoalForestArea.containsGround(mainPos)) {
            return false;
        }

        if (!PublicAreaDailyWorkUnits.isChunkLoadedNow(
                level, mainPos.getX(), mainPos.getZ())) {
            return false;
        }

        if (!level.getBlockState(mainPos.below()).isFaceSturdy(level, mainPos.below(), Direction.UP)) {
            return false;
        }

        if (!level.getBlockState(mainPos).canBeReplaced()) {
            return false;
        }

        Direction facing = Direction.Plane.HORIZONTAL.getRandomDirection(random);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos supportPos = mainPos.offset(dx, -1, dz);
                BlockPos lowerPos = mainPos.offset(dx, 0, dz);
                BlockPos upperPos = lowerPos.above();

                if (!CoalForestArea.containsColumn(lowerPos)) {
                    return false;
                }
                if (!PublicAreaDailyWorkUnits.isChunkLoadedNow(
                        level, lowerPos.getX(), lowerPos.getZ())) {
                    return false;
                }
                if (!level.getBlockState(supportPos).isFaceSturdy(level, supportPos, Direction.UP)) {
                    return false;
                }
                if (!level.getBlockState(lowerPos).canBeReplaced()) {
                    return false;
                }
                if (!level.getBlockState(upperPos).canBeReplaced()) {
                    return false;
                }
            }
        }

        BlockState state = clump.defaultBlockState()
                .setValue(com.stardew.craft.block.decor.MapDecorStaticBlock.PART,
                        com.stardew.craft.block.decor.MapDecorStaticBlock.Part.MAIN)
                .setValue(com.stardew.craft.block.decor.MapDecorStaticBlock.FACING, facing);
        level.setBlock(mainPos, state, Block.UPDATE_ALL);
        clump.setPlacedBy(level, mainPos, state, null, ItemStack.EMPTY);
        return true;
    }

    private static final class CoalForestClumpInitData extends SavedData {
        private boolean initialized;

        private CoalForestClumpInitData(boolean initialized) {
            this.initialized = initialized;
        }

        static SavedData.Factory<CoalForestClumpInitData> factory() {
            return new SavedData.Factory<>(
                    () -> new CoalForestClumpInitData(false),
                    CoalForestClumpInitData::load);
        }

        static CoalForestClumpInitData load(CompoundTag tag, HolderLookup.Provider registries) {
            return new CoalForestClumpInitData(tag.getBoolean("Initialized"));
        }

        boolean initialized() {
            return initialized;
        }

        void setInitialized(boolean value) {
            if (initialized != value) {
                initialized = value;
                setDirty();
            }
        }

        @Override
        public CompoundTag save(@Nonnull CompoundTag tag, @Nonnull HolderLookup.Provider registries) {
            tag.putBoolean("Initialized", initialized);
            return tag;
        }
    }

}
