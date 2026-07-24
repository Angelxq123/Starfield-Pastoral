package com.stardew.craft.manager;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.api.v1.world.StardewForageZoneDefinition;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.nature.ForageBlock;
import com.stardew.craft.time.StardewTimeManager;
import com.stardew.craft.time.settlement.DailySettlementContext;
import com.stardew.craft.time.settlement.DailySettlementContextFactory;
import com.stardew.craft.time.settlement.DailySettlementRandom;
import com.stardew.craft.time.settlement.DailySettlementWorkUnit;
import com.stardew.craft.time.settlement.DailySettlementWorkUnits;
import com.stardew.craft.world.data.ForageZoneData;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.TallGrassBlock;
import net.minecraft.world.level.block.FlowerBlock;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.registries.DeferredBlock;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * SDV-parity forage spawning service.
 * Called once per day from StardewTimeManager.advanceDayWithSleepTime().
 *
 * <p>Replicates GameLocation.spawnObjects() logic:
 * <ul>
 *   <li>Each zone has MinDailyForageSpawn, MaxDailyForageSpawn, MaxSpawnedForageAtOnce</li>
 *   <li>Each forage entry has a season filter and a chance</li>
 *   <li>Random position within zone bounds; SDV uses 11 attempts, this map uses 30 for denser MC terrain</li>
 *   <li>Must be on top of a natural spawnable surface (public areas) or sand (Beach/Desert)</li>
 *   <li>Must be outdoors (sky visible) for non-beach zones</li>
 * </ul>
 */
@SuppressWarnings("null")
public final class ForageSpawnService {

    private static final String INIT_DATA_ID = "stardewcraft_forage_init";

    private ForageSpawnService() {}

    // ======================== Forage Entry ========================

    private record ForageEntry(Supplier<? extends Block> block, int season, double chance) {
        /** season = -1 means all seasons */
        boolean matchesSeason(int currentSeason) {
            return season == -1 || season == currentSeason;
        }
    }

    // ======================== Zone Definition ========================

    /**
     * A rectangular region in the Stardew dimension where forage can spawn.
     */
    private record ZoneRect(int minX, int minY, int minZ, int maxX, int maxY, int maxZ, int weight) {
        boolean containsSurfaceY(int y) {
            return y >= minY && y <= maxY;
        }
    }


    private record ForageZone(
            String name,
            List<ZoneRect> rects,
            List<ForageEntry> entries,
            int minDailySpawn,
            int maxDailySpawn,
            int maxSpawnedAtOnce,
            SurfaceType surface
    ) {}

    /** 表面要求：NATURAL = 星露谷室外自然可刷地表；SAND = 必须露天沙子。 */
    private enum SurfaceType { NATURAL, SAND }

    // Season constants used by runtime data and forest-farm forage.
    private static final int SPRING = 0, SUMMER = 1, FALL = 2, WINTER = 3;

    // ======================== Main Entry Point ========================

    /**
     * Called once per day from StardewTimeManager. Replicates SDV GameLocation.spawnObjects().
     */
    public static void onNewDay(ServerLevel level, int season) {
        DailySettlementWorkUnits.drain(createDailyWorkUnit(
                level,
                DailySettlementContextFactory.withSeason(
                        DailySettlementContextFactory.captureCurrentDay(StardewTimeManager.get()),
                        season)));
    }

    public static DailySettlementWorkUnit createDailyWorkUnit(
            ServerLevel level,
            DailySettlementContext context) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(context, "context");
        List<ForageZone> zones = runtimeZones(level);
        long worldSeed = level.getSeed();
        int absoluteDay = context.absoluteDay();
        int season = context.season();
        AtomicInteger totalSpawned = new AtomicInteger();
        List<DailySettlementWorkUnit> zoneWork = new ArrayList<>(zones.size());
        StardewCraft.LOGGER.debug("[ForageSpawn] onNewDay called, season={}", season);
        for (ForageZone zone : zones) {
            zoneWork.add(createZoneDailyWorkUnit(
                    level, zone, season, worldSeed, absoluteDay, totalSpawned));
        }
        return DailySettlementWorkUnits.sequence(
                "forage_daily",
                zoneWork,
                () -> StardewCraft.LOGGER.debug(
                        "[ForageSpawn] Day complete: total spawned = {}", totalSpawned.get()));
    }

    private static DailySettlementWorkUnit createZoneDailyWorkUnit(
            ServerLevel level,
            ForageZone zone,
            int season,
            long worldSeed,
            int absoluteDay,
            AtomicInteger totalSpawned) {
        List<ForageEntry> possibleForage = zone.entries.stream()
                .filter(entry -> entry.matchesSeason(season))
                .toList();
        if (possibleForage.isEmpty()) {
            return DailySettlementWorkUnits.cursor(
                    "forage_zone_" + zone.name,
                    List.<String>of(),
                    value -> value,
                    value -> {},
                    () -> StardewCraft.LOGGER.debug(
                            "[ForageSpawn] {} zone: no forage entries for season {}", zone.name, season));
        }

        AtomicInteger existing = new AtomicInteger();
        List<DailySettlementWorkUnit> scans = new ArrayList<>(zone.rects.size());
        for (int index = 0; index < zone.rects.size(); index++) {
            ZoneRect rect = zone.rects.get(index);
            scans.add(PublicAreaDailyWorkUnits.rectangle(
                    "forage_count_" + zone.name + "_" + index,
                    rect.minX, rect.minZ, rect.maxX, rect.maxZ,
                    (x, z) -> existing.addAndGet(countForageColumn(level, rect, x, z)),
                    () -> existing.get() >= zone.maxSpawnedAtOnce,
                    () -> {}));
        }
        DailySettlementWorkUnit countWork = DailySettlementWorkUnits.sequence(
                "forage_count_" + zone.name, scans, () -> {});
        DailySettlementWorkUnit spawnWork = DailySettlementWorkUnits.deferred(
                "forage_spawn_" + zone.name,
                () -> createForageAttemptWorkUnit(
                        level, zone, possibleForage, existing.get(), worldSeed, absoluteDay,
                        totalSpawned));
        return DailySettlementWorkUnits.sequence(
                "forage_zone_" + zone.name,
                List.of(countWork, spawnWork),
                () -> {});
    }

    private static DailySettlementWorkUnit createForageAttemptWorkUnit(
            ServerLevel level,
            ForageZone zone,
            List<ForageEntry> possibleForage,
            int existing,
            long worldSeed,
            int absoluteDay,
            AtomicInteger totalSpawned) {
        if (existing >= zone.maxSpawnedAtOnce) {
            return DailySettlementWorkUnits.cursor(
                    "forage_spawn_" + zone.name,
                    List.<String>of(),
                    value -> value,
                    value -> {},
                    () -> StardewCraft.LOGGER.debug(
                            "[ForageSpawn] {} zone: already at max ({}/{})",
                            zone.name, existing, zone.maxSpawnedAtOnce));
        }
        long zoneId = stableStringId(zone.name);
        RandomSource countRandom = DailySettlementRandom.forId(
                worldSeed, absoluteDay, "forage_spawn_count", zoneId);
        int rolled = zone.minDailySpawn + countRandom.nextInt(
                zone.maxDailySpawn - zone.minDailySpawn + 1);
        int toSpawn = Math.min(rolled, zone.maxSpawnedAtOnce - existing);
        StardewCraft.LOGGER.debug("[ForageSpawn] {} zone: existing={}, toSpawn={}, possibleEntries={}",
                zone.name, existing, toSpawn, possibleForage.size());
        AtomicInteger spawned = new AtomicInteger();
        return PublicAreaDailyWorkUnits.forageAttempts(
                "forage_spawn_" + zone.name,
                toSpawn,
                30,
                (slot, attempt) -> {
                    long attemptId = zoneId ^ ((long) slot << 32) ^ attempt;
                    RandomSource random = DailySettlementRandom.forId(
                            worldSeed, absoluteDay, "forage_spawn_attempt", attemptId);
                    boolean placed = trySpawnForage(level, zone, possibleForage, random);
                    if (placed) spawned.incrementAndGet();
                    return placed;
                },
                () -> {
                    totalSpawned.addAndGet(spawned.get());
                    StardewCraft.LOGGER.debug(
                            "[ForageSpawn] {} zone: spawned {} forage blocks",
                            zone.name, spawned.get());
                });
    }

    private static boolean trySpawnForage(
            ServerLevel level,
            ForageZone zone,
            List<ForageEntry> possibleForage,
            RandomSource random) {
        ZoneRect rect = pickRandomRect(zone, random);
        int x = rect.minX + random.nextInt(rect.maxX - rect.minX + 1);
        int z = rect.minZ + random.nextInt(rect.maxZ - rect.minZ + 1);
        if (!PublicAreaDailyWorkUnits.isChunkLoadedNow(level, x, z)) return false;

        int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
        BlockPos surfacePos = new BlockPos(x, surfaceY, z);
        BlockState surfaceState = level.getBlockState(surfacePos);
        if (isReplaceablePlant(surfaceState)) {
            surfacePos = surfacePos.below();
            surfaceState = level.getBlockState(surfacePos);
        }
        if (!rect.containsSurfaceY(surfacePos.getY())) return false;
        BlockPos placePos = surfacePos.above();
        if (surfaceState.isAir() || surfaceState.getFluidState().isSource()) return false;
        if (!canPlaceForage(level, surfacePos, placePos, zone.surface)) return false;

        ForageEntry chosen = possibleForage.get(random.nextInt(possibleForage.size()));
        if (random.nextDouble() > chosen.chance) return false;
        BlockState existing = level.getBlockState(placePos);
        if (!existing.isAir() && isReplaceablePlant(existing)) {
            level.destroyBlock(placePos, false);
        }
        level.setBlock(placePos, chosen.block.get().defaultBlockState(), Block.UPDATE_ALL);
        return true;
    }

    // ======================== Helpers ========================

    private static List<ForageZone> runtimeZones(ServerLevel level) {
        List<ForageZone> result = new ArrayList<>();
        for (var registered : ForageZoneData.available(level)) {
            StardewForageZoneDefinition definition = registered.getValue();
            List<ZoneRect> rects = definition.areas().stream()
                    .map(area -> new ZoneRect(area.minX(), area.minY(), area.minZ(),
                            area.maxX(), area.maxY(), area.maxZ(), area.weight()))
                    .toList();
            List<ForageEntry> entries = new ArrayList<>();
            for (StardewForageZoneDefinition.Entry entry : definition.entries()) {
                if (!BuiltInRegistries.BLOCK.containsKey(entry.block())) {
                    StardewCraft.LOGGER.error("[Forage data] Zone {} references unknown block {}",
                            registered.getKey(), entry.block());
                    continue;
                }
                Block block = BuiltInRegistries.BLOCK.get(entry.block());
                for (String season : entry.seasons()) {
                    entries.add(new ForageEntry(() -> block, seasonIndex(season), entry.chance()));
                }
            }
            if (entries.isEmpty()) continue;
            result.add(new ForageZone(
                    registered.getKey().toString(),
                    rects,
                    List.copyOf(entries),
                    definition.minDailySpawn(),
                    definition.maxDailySpawn(),
                    definition.maxSpawnedAtOnce(),
                    definition.surface() == StardewForageZoneDefinition.Surface.SAND
                            ? SurfaceType.SAND : SurfaceType.NATURAL));
        }
        return List.copyOf(result);
    }

    private static int seasonIndex(String season) {
        return switch (season) {
            case "summer" -> SUMMER;
            case "fall" -> FALL;
            case "winter" -> WINTER;
            default -> SPRING;
        };
    }

    private static ZoneRect pickRandomRect(ForageZone zone, RandomSource random) {
        List<ZoneRect> rects = zone.rects;
        if (rects.size() == 1) return rects.get(0);
        int totalWeight = rects.stream().mapToInt(ZoneRect::weight).sum();
        int roll = random.nextInt(totalWeight);
        for (ZoneRect rect : rects) {
            roll -= rect.weight();
            if (roll < 0) return rect;
        }
        return rects.getLast();
    }

    /**
     * Check if forage can be placed at placePos on top of surfacePos.
     */
    private static boolean canPlaceForage(ServerLevel level, BlockPos surfacePos, BlockPos placePos,
                                          SurfaceType surface) {
        BlockState surfaceState = level.getBlockState(surfacePos);
        BlockState placeState = level.getBlockState(placePos);

        // Must be air or a replaceable plant (grass, flowers, ferns) at placement position
        if (!placeState.isAir() && !isReplaceablePlant(placeState)) return false;

        // Must see sky (outdoors check)
        if (!level.canSeeSky(placePos)) return false;

        return switch (surface) {
            // SDV uses the map's Back-layer "Spawnable" property, not only grass.
            // In this MC map, public valley spawnable ground may be grass or yellow/natural dirt.
            case NATURAL -> isNaturalForageSurface(surfaceState);
            case SAND -> surfaceState.is(Blocks.SAND);
        };
    }

    private static boolean isNaturalForageSurface(BlockState state) {
        Block block = state.getBlock();
        if (block == ModBlocks.ARTIFACT_SPOT_DIRT.get()) {
            return false;
        }
        if (block == Blocks.GRASS_BLOCK || block == ModBlocks.YELLOW_DIRT.get()) {
            return true;
        }
        return state.is(BlockTags.DIRT);
    }

    /**
     * Returns true if the block state is a weak decorative plant that forage can replace.
     * Includes short grass, tall grass, flowers, ferns, and double-tall plants.
     */
    private static boolean isReplaceablePlant(BlockState state) {
        Block block = state.getBlock();
        if (block instanceof ForageBlock) return false;
        // Our mod's wild weeds (杂草)
        if (block instanceof com.stardew.craft.block.nature.WildWeedsBlock) return true;
        // Short grass and fern
        if (block == Blocks.SHORT_GRASS || block == Blocks.FERN) return true;
        // Tall grass and large fern
        if (block == Blocks.TALL_GRASS || block == Blocks.LARGE_FERN) return true;
        // All vanilla small flowers (poppy, dandelion, cornflower, etc.)
        if (block instanceof FlowerBlock) return true;
        // Double-tall flowers (sunflower, lilac, rose bush, peony)
        if (block instanceof DoublePlantBlock) return true;
        // Generic bush check for any modded short plants
        if (block instanceof TallGrassBlock) return true;
        // Check if the block is replaceable by world generation (covers most decorative plants)
        return state.canBeReplaced();
    }

    private static int countForageColumn(ServerLevel level, ZoneRect rect, int x, int z) {
        if (!PublicAreaDailyWorkUnits.isChunkLoadedNow(level, x, z)) return 0;
        int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
        if (!rect.containsSurfaceY(surfaceY)) return 0;
        return countForageAtColumn(level, x, z);
    }

    private static int countForageAtColumn(ServerLevel level, int x, int z) {
        int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
        int count = 0;
        for (int y = surfaceY - 1; y <= surfaceY + 3; y++) {
            BlockPos pos = new BlockPos(x, y, z);
            if (level.getBlockState(pos).getBlock() instanceof ForageBlock) {
                count++;
            }
        }
        return count;
    }

    // ======================== First-Day Initial Spawn ========================

    /**
     * Called on first entry into the Stardew dimension. Ensures forage exists on Day 1.
     * Uses SavedData to guarantee it only runs once per world.
     */
    public static void ensureInitialSpawn(ServerLevel level, int season) {
        if (!level.dimension().equals(com.stardew.craft.core.ModDimensions.STARDEW_VALLEY)) return;

        ForageInitData data = level.getDataStorage().computeIfAbsent(
                ForageInitData.factory(), INIT_DATA_ID);
        if (data.isInitialized()) return;

        StardewCraft.LOGGER.debug("[ForageSpawn] Running first-day initial forage spawn (season={})", season);
        onNewDay(level, season);
        data.markInitialized();
    }

    public static class ForageInitData extends SavedData {
        private boolean initialized;

        public ForageInitData() {}

        private ForageInitData(CompoundTag tag) {
            this.initialized = tag.getBoolean("Initialized");
        }

        public boolean isInitialized() { return initialized; }

        public void markInitialized() {
            this.initialized = true;
            setDirty();
        }

        @Override
        @Nonnull
        public CompoundTag save(@Nonnull CompoundTag tag, @Nonnull HolderLookup.Provider registries) {
            tag.putBoolean("Initialized", initialized);
            return tag;
        }

        public static SavedData.Factory<ForageInitData> factory() {
            return new SavedData.Factory<>(ForageInitData::new, (tag, provider) -> new ForageInitData(tag));
        }
    }

    // ======================== Forest Farm Forage ========================

    /**
     * SDV parity: Forest farm spawns seasonal forage in its dedicated forage zone daily.
     * Called once per day from StardewTimeManager, after the public-area onNewDay().
     *
     * <p>Items per season (equal 25% weight each):
     * <ul>
     *   <li>Spring: Wild Horseradish, Daffodil, Leek, Dandelion</li>
     *   <li>Summer: Spice Berry, Sweet Pea, Fiddlehead Fern, Common Mushroom</li>
     *   <li>Fall: Wild Plum, Hazelnut, Blackberry, Chanterelle</li>
     *   <li>Winter: no spawning</li>
     * </ul>
     */
    private static final List<List<DeferredBlock<Block>>> FOREST_FARM_FORAGE = List.of(
            // Spring
            List.of(ModBlocks.FORAGE_WILD_HORSERADISH, ModBlocks.FORAGE_DAFFODIL,
                    ModBlocks.FORAGE_LEEK, ModBlocks.FORAGE_DANDELION),
            // Summer
            List.of(ModBlocks.FORAGE_SPICE_BERRY, ModBlocks.FORAGE_SWEET_PEA,
                    ModBlocks.FORAGE_FIDDLEHEAD_FERN, ModBlocks.FORAGE_COMMON_MUSHROOM),
            // Fall
            List.of(ModBlocks.FORAGE_WILD_PLUM, ModBlocks.FORAGE_HAZELNUT,
                    ModBlocks.FORAGE_BLACKBERRY, ModBlocks.FORAGE_CHANTERELLE)
    );

    private static final int FOREST_FARM_MIN_SPAWN = 1;
    private static final int FOREST_FARM_MAX_SPAWN = 4;
    private static final int FOREST_FARM_MAX_AT_ONCE = 6;

    /**
     * Spawns seasonal forage on all forest-type farms (public area + each player's farm instance).
     * Called from StardewTimeManager.advanceDayWithSleepTime().
     */
    public static void onNewDayForestFarms(ServerLevel level, int season) {
        DailySettlementWorkUnits.drain(createForestFarmDailyWorkUnit(
                level,
                DailySettlementContextFactory.withSeason(
                        DailySettlementContextFactory.captureCurrentDay(StardewTimeManager.get()),
                        season)));
    }

    public static DailySettlementWorkUnit createForestFarmDailyWorkUnit(
            ServerLevel level,
            DailySettlementContext context) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(context, "context");
        int season = context.season();
        if (season == WINTER || season < 0 || season > 2) {
            return DailySettlementWorkUnits.sequence("forest_farm_forage_daily", List.of(), () -> {});
        }

        List<DeferredBlock<Block>> possibleForage = FOREST_FARM_FORAGE.get(season);
        List<ForestFarmDailyEntry> farmSnapshot = new ArrayList<>();
        for (com.stardew.craft.farm.FarmInstance farm
                : com.stardew.craft.farm.FarmInstanceRegistry.get().getAllFarms()) {
            if (farm.getFarmType() != com.stardew.craft.farm.FarmType.FOREST) continue;
            com.stardew.craft.farm.FarmType.FarmLayout layout = farm.getFarmType().getLayout();
            if (layout == null || layout.forageZoneMin() == null || layout.forageZoneMax() == null) continue;
            BlockPos zoneMin = farm.getOrigin().offset(layout.forageZoneMin());
            BlockPos zoneMax = farm.getOrigin().offset(layout.forageZoneMax());
            farmSnapshot.add(new ForestFarmDailyEntry(
                    farm.getOwnerUUID(),
                    farm.getOwnerName(),
                    Math.min(zoneMin.getX(), zoneMax.getX()),
                    Math.min(zoneMin.getZ(), zoneMax.getZ()),
                    Math.max(zoneMin.getX(), zoneMax.getX()),
                    Math.max(zoneMin.getZ(), zoneMax.getZ())));
        }
        farmSnapshot.sort(Comparator.comparing(entry -> entry.ownerId().toString()));

        long worldSeed = level.getSeed();
        int absoluteDay = context.absoluteDay();
        AtomicInteger totalSpawned = new AtomicInteger();
        List<DailySettlementWorkUnit> farmWork = new ArrayList<>(farmSnapshot.size());
        for (ForestFarmDailyEntry farm : farmSnapshot) {
            farmWork.add(createForestFarmWorkUnit(
                    level, farm, possibleForage, worldSeed, absoluteDay, totalSpawned));
        }
        return DailySettlementWorkUnits.sequence(
                "forest_farm_forage_daily",
                farmWork,
                () -> {
                    if (totalSpawned.get() > 0) {
                        StardewCraft.LOGGER.debug(
                                "[ForageSpawn] Forest farms total: {} forage spawned",
                                totalSpawned.get());
                    }
                });
    }

    private static DailySettlementWorkUnit createForestFarmWorkUnit(
            ServerLevel level,
            ForestFarmDailyEntry farm,
            List<DeferredBlock<Block>> possibleForage,
            long worldSeed,
            int absoluteDay,
            AtomicInteger totalSpawned) {
        AtomicInteger existing = new AtomicInteger();
        DailySettlementWorkUnit scan = PublicAreaDailyWorkUnits.rectangle(
                "forest_farm_forage_count_" + farm.ownerId(),
                farm.minX(), farm.minZ(), farm.maxX(), farm.maxZ(),
                (x, z) -> {
                    if (PublicAreaDailyWorkUnits.isChunkLoadedNow(level, x, z)) {
                        existing.addAndGet(countForageAtColumn(level, x, z));
                    }
                },
                () -> existing.get() >= FOREST_FARM_MAX_AT_ONCE,
                () -> {});
        DailySettlementWorkUnit spawn = DailySettlementWorkUnits.deferred(
                "forest_farm_forage_spawn_" + farm.ownerId(),
                () -> createForestFarmAttemptWorkUnit(
                        level, farm, possibleForage, existing.get(), worldSeed, absoluteDay,
                        totalSpawned));
        return DailySettlementWorkUnits.sequence(
                "forest_farm_forage_" + farm.ownerId(), List.of(scan, spawn), () -> {});
    }

    private static DailySettlementWorkUnit createForestFarmAttemptWorkUnit(
            ServerLevel level,
            ForestFarmDailyEntry farm,
            List<DeferredBlock<Block>> possibleForage,
            int existing,
            long worldSeed,
            int absoluteDay,
            AtomicInteger totalSpawned) {
        int capacity = Math.max(0, FOREST_FARM_MAX_AT_ONCE - existing);
        long farmId = stableUuid(farm.ownerId());
        RandomSource countRandom = DailySettlementRandom.forId(
                worldSeed, absoluteDay, "forest_farm_forage_count", farmId);
        int rolled = FOREST_FARM_MIN_SPAWN + countRandom.nextInt(
                FOREST_FARM_MAX_SPAWN - FOREST_FARM_MIN_SPAWN + 1);
        int toSpawn = Math.min(rolled, capacity);
        AtomicInteger spawned = new AtomicInteger();
        return PublicAreaDailyWorkUnits.forageAttempts(
                "forest_farm_forage_spawn_" + farm.ownerId(),
                toSpawn,
                30,
                (slot, attempt) -> {
                    RandomSource random = DailySettlementRandom.forId(
                            worldSeed,
                            absoluteDay,
                            "forest_farm_forage_attempt",
                            farmId ^ ((long) slot << 32) ^ attempt);
                    boolean placed = trySpawnForestFarmForage(
                            level, farm, possibleForage, random);
                    if (placed) spawned.incrementAndGet();
                    return placed;
                },
                () -> {
                    totalSpawned.addAndGet(spawned.get());
                    StardewCraft.LOGGER.debug(
                            "[ForageSpawn] Forest farm ({}): spawned {} forage in zone",
                            farm.ownerName(), spawned.get());
                });
    }

    private static boolean trySpawnForestFarmForage(
            ServerLevel level,
            ForestFarmDailyEntry farm,
            List<DeferredBlock<Block>> possibleForage,
            RandomSource random) {
        int x = farm.minX() + random.nextInt(farm.maxX() - farm.minX() + 1);
        int z = farm.minZ() + random.nextInt(farm.maxZ() - farm.minZ() + 1);
        if (!PublicAreaDailyWorkUnits.isChunkLoadedNow(level, x, z)) return false;
        int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
        BlockPos surfacePos = new BlockPos(x, surfaceY, z);
        BlockState surfaceState = level.getBlockState(surfacePos);
        if (isReplaceablePlant(surfaceState)) {
            surfacePos = surfacePos.below();
            surfaceState = level.getBlockState(surfacePos);
        }
        BlockPos placePos = surfacePos.above();
        if (surfaceState.isAir() || surfaceState.getFluidState().isSource()) return false;
        if (!canPlaceForage(level, surfacePos, placePos, SurfaceType.NATURAL)) return false;
        DeferredBlock<Block> chosen = possibleForage.get(random.nextInt(possibleForage.size()));
        BlockState existingState = level.getBlockState(placePos);
        if (!existingState.isAir() && isReplaceablePlant(existingState)) {
            level.destroyBlock(placePos, false);
        }
        level.setBlock(placePos, chosen.get().defaultBlockState(), Block.UPDATE_ALL);
        return true;
    }

    private static long stableStringId(String value) {
        long hash = 0xcbf29ce484222325L;
        for (int index = 0; index < value.length(); index++) {
            hash ^= value.charAt(index);
            hash *= 0x100000001b3L;
        }
        return hash;
    }

    private static long stableUuid(UUID ownerId) {
        return ownerId.getMostSignificantBits() ^ ownerId.getLeastSignificantBits();
    }

    private record ForestFarmDailyEntry(
            UUID ownerId,
            String ownerName,
            int minX,
            int minZ,
            int maxX,
            int maxZ) {
    }
}
