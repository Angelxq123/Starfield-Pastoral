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

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

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
        if (!level.hasChunk(x >> 4, z >> 4)) return;
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

        List<ChunkPos> forcedChunks = forceRegionChunks(level);
        int spawned;
        try {
            spawned = runInitialSpawn(level);
        } finally {
            releaseRegionChunks(level, forcedChunks);
        }
        if (initialSpawnComplete(spawned)) {
            data.setInitialized(true);
        }
    }

    private static int runInitialSpawn(ServerLevel level) {
        clearExistingInitial(level);
        RandomSource random = level.getRandom();
        int spawned = 0;
        for (BlockPos pos : LARGE_STUMP_POSITIONS) {
            if (tryPlaceAt(level, random, ModBlocks.LARGE_STUMP.get(), pos)) {
                spawned++;
            } else {
                StardewCraft.LOGGER.warn("[SECRET_WOODS] Failed to place large stump at {}", pos);
            }
        }
        StardewCraft.LOGGER.info("[SECRET_WOODS] Initial stump spawn: largeStump={}/{}",
                spawned, LARGE_STUMP_POSITIONS.size());
        return spawned;
    }

    private static boolean initialSpawnComplete(int spawned) {
        return spawned > 0;
    }

    private static List<ChunkPos> forceRegionChunks(ServerLevel level) {
        List<ChunkPos> newlyForced = new ArrayList<>();
        try {
            int minChunkX = CoalForestArea.MIN_X >> 4;
            int maxChunkX = CoalForestArea.MAX_X >> 4;
            int minChunkZ = CoalForestArea.MIN_Z >> 4;
            int maxChunkZ = CoalForestArea.MAX_Z >> 4;
            for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
                for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                    long chunkKey = ChunkPos.asLong(chunkX, chunkZ);
                    if (!level.getForcedChunks().contains(chunkKey)) {
                        level.setChunkForced(chunkX, chunkZ, true);
                        newlyForced.add(new ChunkPos(chunkX, chunkZ));
                    }
                    level.getChunk(chunkX, chunkZ);
                }
            }
            return List.copyOf(newlyForced);
        } catch (RuntimeException | Error failure) {
            releaseRegionChunks(level, newlyForced);
            throw failure;
        }
    }

    private static void releaseRegionChunks(ServerLevel level, List<ChunkPos> forcedChunks) {
        for (ChunkPos chunk : forcedChunks) {
            level.setChunkForced(chunk.x, chunk.z, false);
        }
    }

    private static void clearExistingInitial(ServerLevel level) {
        for (int x = CoalForestArea.MIN_X; x <= CoalForestArea.MAX_X; x++) {
            for (int z = CoalForestArea.MIN_Z; z <= CoalForestArea.MAX_Z; z++) {
                processClearColumn(level, x, z);
            }
        }
    }

    private static boolean tryPlaceAt(ServerLevel level, RandomSource random, Block block, BlockPos mainPos) {
        if (!(block instanceof ResourceClumpBlock clump)) {
            return false;
        }

        if (!CoalForestArea.containsGround(mainPos)) {
            return false;
        }

        if (!level.hasChunk(mainPos.getX() >> 4, mainPos.getZ() >> 4)) {
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
                if (!level.hasChunk(lowerPos.getX() >> 4, lowerPos.getZ() >> 4)) {
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
