package com.stardew.craft.manager;

import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.nature.PastureGrassBlock;
import com.stardew.craft.farm.FarmDailyProcessHelper;
import com.stardew.craft.farm.FarmInstance;
import com.stardew.craft.farm.FarmInstanceRegistry;
import com.stardew.craft.time.StardewTimeManager;
import com.stardew.craft.time.settlement.DailySettlementContext;
import com.stardew.craft.time.settlement.DailySettlementContextFactory;
import com.stardew.craft.time.settlement.DailySettlementRandom;
import com.stardew.craft.time.settlement.DailySettlementWorkUnit;
import com.stardew.craft.time.settlement.DailySettlementWorkUnits;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Budgeted implementation of SDV's daily farm grass spawning and growth. */
@SuppressWarnings("null")
public class PastureGrassGrowthManager extends SavedData {
    private static final String DATA_NAME = "stardew_pasture_grass_growth";
    private boolean processing;

    public static PastureGrassGrowthManager get(ServerLevel level) {
        ServerLevel overworld = level.getServer().overworld();
        return overworld.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(PastureGrassGrowthManager::new,
                        (tag, provider) -> new PastureGrassGrowthManager()),
                DATA_NAME);
    }

    public void growDaily(ServerLevel level) {
        DailySettlementContext context = DailySettlementContextFactory.captureCurrentDay(
                StardewTimeManager.get());
        DailySettlementWorkUnits.drain(createDailyWorkUnit(
                level, context, snapshotOnlineFarms(level)));
    }

    public DailySettlementWorkUnit createDailyWorkUnit(
            ServerLevel level, DailySettlementContext context) {
        return createDailyWorkUnit(level, context, snapshotOnlineFarms(level));
    }

    public DailySettlementWorkUnit createDailyWorkUnit(
            ServerLevel level,
            DailySettlementContext context,
            Map<UUID, FarmInstance> frozenFarms) {
        if (processing) {
            throw new IllegalStateException("Pasture grass daily work is already active");
        }
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(frozenFarms, "frozenFarms");
        processing = true;
        try {
            List<FarmInstance> farms = frozenFarms.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey(java.util.Comparator.comparing(UUID::toString)))
                    .map(Map.Entry::getValue)
                    .filter(FarmInstance::isInitialized)
                    .toList();
            Set<UUID> canceledDailySpawns = new HashSet<>();
            List<SpawnTask> spawnTasks = createSpawnTasks(level, context, farms);
            DailySettlementWorkUnit spawns = DailySettlementWorkUnits.cursor(
                    "pasture_grass_spawn",
                    spawnTasks,
                    SpawnTask::identity,
                    task -> processSpawnTask(level, context, task, canceledDailySpawns),
                    () -> {});
            List<BlockPos> grassPositions = new ArrayList<>();
            List<PastureScanTask> scanTasks = createPastureScanTasks(farms);
            DailySettlementWorkUnit scan = DailySettlementWorkUnits.cursor(
                    "pasture_grass_scan",
                    scanTasks,
                    PastureScanTask::identity,
                    task -> scanPastureGrassChunk(level, task, grassPositions),
                    () -> {});
            DailySettlementWorkUnit growth = DailySettlementWorkUnits.deferred(
                    "pasture_grass_growth",
                    () -> new PastureGrowthWorkUnit(
                            level,
                            context,
                            grassPositions,
                            context.season() == 0 && context.day() == 1
                                    && context.absoluteDay() > 1 ? 41 : 1));
            return DailySettlementWorkUnits.sequence(
                    "pasture_grass", List.of(spawns, scan, growth),
                    this::finishDailyProcessing);
        } catch (RuntimeException | Error exception) {
            finishDailyProcessing();
            throw exception;
        }
    }

    private static List<SpawnTask> createSpawnTasks(
            ServerLevel level, DailySettlementContext context, List<FarmInstance> farms) {
        if (context.season() == 3) {
            return List.of();
        }
        List<SpawnTask> tasks = new ArrayList<>();
        for (FarmInstance farm : farms) {
            UUID ownerId = farm.getOwnerUUID();
            RandomSource countRandom = DailySettlementRandom.forId(
                    level.getSeed(), context.absoluteDay(), "pasture_grass_spawn_count",
                    ownerId.getMostSignificantBits() ^ ownerId.getLeastSignificantBits());
            int dailyAttempts = countRandom.nextInt(5) + 1;
            if (context.season() == 0 && context.day() == 1) {
                dailyAttempts *= 15;
            }
            for (int attempt = 0; attempt < dailyAttempts; attempt++) {
                tasks.add(new SpawnTask(ownerId, farm, attempt, false));
            }
            if (context.season() == 0 && context.day() == 1 && context.absoluteDay() > 1) {
                for (int attempt = 0; attempt < 15; attempt++) {
                    tasks.add(new SpawnTask(ownerId, farm, attempt, true));
                }
            }
        }
        return List.copyOf(tasks);
    }

    private static void processSpawnTask(
            ServerLevel level,
            DailySettlementContext context,
            SpawnTask task,
            Set<UUID> canceledDailySpawns) {
        if (!task.springPlacement && canceledDailySpawns.contains(task.ownerId)) {
            return;
        }
        long stableId = task.ownerId.getMostSignificantBits()
                ^ task.ownerId.getLeastSignificantBits()
                ^ ((long) task.attempt << 1)
                ^ (task.springPlacement ? 1L : 0L);
        RandomSource random = DailySettlementRandom.forId(
                level.getSeed(), context.absoluteDay(), "pasture_grass_spawn", stableId);
        if (task.springPlacement) {
            BlockPos column = randomFarmColumn(task.farm, random);
            try (var lease = FarmDailyProcessHelper.leasePosition(level, column, 0)) {
                BlockPos place = findRandomGrassPlace(
                        level, task.farm, column.getX(), column.getZ());
                if (place != null) {
                    placeGrass(level, place, 4, random);
                }
            }
            return;
        }

        for (int tries = 0; tries < 3; tries++) {
            BlockPos column = randomFarmColumn(task.farm, random);
            try (var lease = FarmDailyProcessHelper.leasePosition(level, column, 0)) {
                BlockPos place = findRandomGrassPlace(
                        level, task.farm, column.getX(), column.getZ());
                boolean grass = random.nextDouble() < 0.15D;
                boolean treeRoll = !grass && random.nextDouble() < 0.35D;
                if (treeRoll) {
                    if (random.nextDouble() < 0.25D) {
                        canceledDailySpawns.add(task.ownerId);
                        return;
                    }
                    continue;
                }
                if (grass && place != null) {
                    placeGrass(level, place, random.nextInt(2) + 1, random);
                }
            }
        }
    }

    private static void processPastureGrassDay(
            ServerLevel level,
            BlockPos pos,
            DailySettlementContext context,
            int pass,
            Set<Long> knownPositions,
            List<BlockPos> grassPositions,
            FarmDailyProcessHelper.ReusingPositionLease leaseCursor) {
        try (var lease = leaseCursor.lease(pos)) {
            if (!level.isLoaded(pos)) {
                return;
            }
            BlockState grass = level.getBlockState(pos);
            if (!(grass.getBlock() instanceof PastureGrassBlock)) {
                return;
            }
            if (context.season() == 3) {
                level.removeBlock(pos, false);
                return;
            }

            RandomSource random = DailySettlementRandom.forPosition(
                    level.getSeed(), context.absoluteDay(), "pasture_grass_" + pass, pos);
            if (!FarmDailyDecisions.rollGrassSource(random)) {
                return;
            }
            int clumps = grass.getValue(PastureGrassBlock.CLUMPS);
            if (clumps < 4) {
                int grown = Math.min(4, clumps + random.nextInt(3));
                if (grown != clumps) {
                    level.setBlock(pos,
                            grass.setValue(PastureGrassBlock.CLUMPS, grown), Block.UPDATE_ALL);
                }
                return;
            }

            for (BlockPos neighbor : List.of(pos.north(), pos.south(), pos.east(), pos.west())) {
                if (!level.isLoaded(neighbor) || !FarmDailyDecisions.rollGrassNeighbor(random)
                        || !level.getBlockState(neighbor).isAir()) {
                    continue;
                }
                BlockState spread = grass.getBlock().defaultBlockState()
                        .setValue(PastureGrassBlock.VARIANT,
                                FarmDailyDecisions.rollGrassVariant(random))
                        .setValue(PastureGrassBlock.CLUMPS, random.nextInt(2) + 1);
                if (isDiggableFarmGround(level.getBlockState(neighbor.below()).getBlock())
                        && spread.canSurvive(level, neighbor)) {
                    level.setBlock(neighbor, spread, Block.UPDATE_ALL);
                    if (knownPositions.add(neighbor.asLong())) {
                        grassPositions.add(neighbor.immutable());
                    }
                }
            }
        }
    }

    private static void placeGrass(
            ServerLevel level, BlockPos pos, int clumps, RandomSource random) {
        BlockState grass = ModBlocks.PASTURE_GRASS.get().defaultBlockState()
                .setValue(PastureGrassBlock.VARIANT,
                        random.nextInt(PastureGrassBlock.VISUAL_VARIANT_COUNT))
                .setValue(PastureGrassBlock.CLUMPS, clumps);
        if (grass.canSurvive(level, pos)) {
            level.setBlock(pos, grass, Block.UPDATE_ALL);
        }
    }

    @Nullable
    private static BlockPos findRandomGrassPlace(
            ServerLevel level, FarmInstance farm, int x, int z) {
        BlockPos min = farm.getFarmBoundsMin();
        BlockPos max = farm.getFarmBoundsMax();
        for (int y = max.getY(); y >= min.getY(); y--) {
            BlockPos ground = new BlockPos(x, y, z);
            if (!level.isLoaded(ground)) {
                return null;
            }
            BlockState groundState = level.getBlockState(ground);
            if (groundState.isAir()) {
                continue;
            }
            BlockPos place = ground.above();
            return isDiggableFarmGround(groundState.getBlock())
                    && farm.contains(ground) && level.getBlockState(place).isAir() ? place : null;
        }
        return null;
    }

    private static BlockPos randomFarmColumn(FarmInstance farm, RandomSource random) {
        BlockPos min = farm.getFarmBoundsMin();
        BlockPos max = farm.getFarmBoundsMax();
        int x = min.getX() + random.nextInt(max.getX() - min.getX() + 1);
        int z = min.getZ() + random.nextInt(max.getZ() - min.getZ() + 1);
        return new BlockPos(x, min.getY(), z);
    }

    private static boolean isDiggableFarmGround(Block block) {
        return block == ModBlocks.YELLOW_DIRT.get() || block == Blocks.GRASS_BLOCK;
    }

    private static List<PastureScanTask> createPastureScanTasks(List<FarmInstance> farms) {
        Set<Long> scannedChunks = new HashSet<>();
        List<PastureScanTask> tasks = new ArrayList<>();
        for (FarmInstance farm : farms) {
            BlockPos min = farm.getFarmBoundsMin();
            BlockPos max = farm.getFarmBoundsMax();
            for (int cx = min.getX() >> 4; cx <= max.getX() >> 4; cx++) {
                for (int cz = min.getZ() >> 4; cz <= max.getZ() >> 4; cz++) {
                    long key = net.minecraft.world.level.ChunkPos.asLong(cx, cz);
                    if (scannedChunks.add(key)) {
                        tasks.add(new PastureScanTask(farm, cx, cz));
                    }
                }
            }
        }
        return List.copyOf(tasks);
    }

    private static void scanPastureGrassChunk(
            ServerLevel level,
            PastureScanTask task,
            List<BlockPos> results) {
        BlockPos farmMin = task.farm().getFarmBoundsMin();
        BlockPos farmMax = task.farm().getFarmBoundsMax();
        int minX = Math.max(farmMin.getX(), task.chunkX() << 4);
        int maxX = Math.min(farmMax.getX(), (task.chunkX() << 4) + 15);
        int minZ = Math.max(farmMin.getZ(), task.chunkZ() << 4);
        int maxZ = Math.min(farmMax.getZ(), (task.chunkZ() << 4) + 15);
        BlockPos leaseMin = new BlockPos(minX, farmMin.getY(), minZ);
        BlockPos leaseMax = new BlockPos(maxX, farmMax.getY(), maxZ);
        try (var lease = FarmDailyProcessHelper.leaseBounds(level, leaseMin, leaseMax)) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    int top = level.getHeight(
                            net.minecraft.world.level.levelgen.Heightmap.Types
                                    .MOTION_BLOCKING_NO_LEAVES,
                            x, z);
                    int minY = Math.max(farmMin.getY(), top - 3);
                    int maxY = Math.min(farmMax.getY(), top + 1);
                    for (int y = minY; y <= maxY; y++) {
                        BlockPos pos = new BlockPos(x, y, z);
                        if (level.getBlockState(pos).getBlock() instanceof PastureGrassBlock) {
                            results.add(pos.immutable());
                        }
                    }
                }
            }
        }
    }

    private static Map<UUID, FarmInstance> snapshotOnlineFarms(ServerLevel level) {
        FarmInstanceRegistry registry = FarmInstanceRegistry.get();
        Map<UUID, FarmInstance> farms = new LinkedHashMap<>();
        for (var player : level.players()) {
            FarmInstance farm = registry.getFarmForPlayer(player.getUUID());
            if (farm != null) {
                farms.putIfAbsent(farm.getOwnerUUID(), farm);
            }
        }
        return Map.copyOf(farms);
    }

    private void finishDailyProcessing() {
        processing = false;
    }

    @Override
    public net.minecraft.nbt.CompoundTag save(
            @Nonnull net.minecraft.nbt.CompoundTag tag,
            @Nonnull net.minecraft.core.HolderLookup.Provider provider) {
        return tag;
    }

    private record SpawnTask(
            UUID ownerId, FarmInstance farm, int attempt, boolean springPlacement) {
        private String identity() {
            return ownerId + ":" + (springPlacement ? "spring" : "daily") + ":" + attempt;
        }
    }

    private record PastureScanTask(FarmInstance farm, int chunkX, int chunkZ) {
        private String identity() {
            return farm.getOwnerUUID() + ":" + chunkX + "," + chunkZ;
        }
    }

    private static final class PastureGrowthWorkUnit implements DailySettlementWorkUnit {
        private final ServerLevel level;
        private final DailySettlementContext context;
        private final List<BlockPos> positions;
        private final Set<Long> knownPositions;
        private final int totalPasses;
        private final FarmDailyProcessHelper.ReusingPositionLease leaseCursor;
        private int pass;
        private int cursor;
        private int passLimit;
        private boolean closed;

        private PastureGrowthWorkUnit(
                ServerLevel level,
                DailySettlementContext context,
                List<BlockPos> positions,
                int totalPasses) {
            this.level = level;
            this.context = context;
            this.positions = new ArrayList<>(positions);
            this.positions.sort(FarmDailyProcessHelper.positionLeaseOrder(1));
            knownPositions = new HashSet<>();
            positions.forEach(pos -> knownPositions.add(pos.asLong()));
            this.totalPasses = totalPasses;
            leaseCursor = FarmDailyProcessHelper.reusingPositionLease(level, 1);
            passLimit = positions.size();
            normalizePass();
        }

        @Override
        public String name() {
            return "pasture_grass_growth";
        }

        @Override
        public String currentItemIdentity() {
            requireCurrent();
            return "pass:" + pass + ":" + positions.get(cursor).toShortString();
        }

        @Override
        public boolean isComplete() {
            normalizePass();
            return pass >= totalPasses;
        }

        @Override
        public void runNext() {
            requireCurrent();
            processPastureGrassDay(
                    level, positions.get(cursor), context, pass, knownPositions, positions,
                    leaseCursor);
            cursor++;
            normalizePass();
        }

        @Override
        public void skipFailedItem() {
            requireCurrent();
            cursor++;
            normalizePass();
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            leaseCursor.close();
        }

        private void normalizePass() {
            while (pass < totalPasses && cursor >= passLimit) {
                pass++;
                cursor = 0;
                passLimit = positions.size();
            }
        }

        private void requireCurrent() {
            if (isComplete()) {
                throw new IllegalStateException("Pasture grass growth is complete");
            }
        }
    }
}
