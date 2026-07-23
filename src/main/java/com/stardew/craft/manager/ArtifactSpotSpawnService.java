package com.stardew.craft.manager;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.nature.ArtifactSpotBlock;
import com.stardew.craft.core.ModDimensions;
import com.stardew.craft.time.StardewTimeManager;
import com.stardew.craft.time.settlement.DailySettlementContext;
import com.stardew.craft.time.settlement.DailySettlementContextFactory;
import com.stardew.craft.time.settlement.DailySettlementRandom;
import com.stardew.craft.time.settlement.DailySettlementWorkUnit;
import com.stardew.craft.time.settlement.DailySettlementWorkUnits;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nonnull;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * SDV-parity artifact spot spawning service.
 *
 * <p>Replicates GameLocation.spawnObjects() artifact-spot section:
 * <ul>
 *   <li>Daily: remove existing spots with 15% chance each, then spawn new ones</li>
 *   <li>Spawn loop: chanceForNewArtifactAttempt starts at 1.0, *= 0.75 per iteration;
 *       +0.10 in winter</li>
 *   <li>Cap: stop if &gt;1 spot exists (winter: &gt;4)</li>
 *   <li>Must be on yellow_dirt, exposed to sky, block above is air</li>
 * </ul>
 *
 * <p>Also handles chunk-load spawning: when a chunk loads the first time during a
 * world session, artifact spots are attempted on any eligible yellow_dirt in that chunk.
 */
@EventBusSubscriber(modid = StardewCraft.MODID)
@SuppressWarnings("null")
public final class ArtifactSpotSpawnService {

    private ArtifactSpotSpawnService() {}

    private static final String INIT_DATA_ID = "stardewcraft_artifact_spot_init";

    // SDV: Farm cap at >0; non-farm cap at >1; winter allows up to 4
    private static final int MAX_SPOTS_FARM = 0;
    private static final int MAX_SPOTS_NON_FARM = 1;
    private static final int MAX_SPOTS_WINTER = 4;
    // 沙漠区因 bbox 巨大，单独给一个略宽的硬上限，超过后停止 bbox 扫描
    private static final int DESERT_DAILY_CAP = 12;
    private static final double BEACH_SAND_DAILY_CHANCE = 0.0004D;
    private static final double DESERT_SAND_DAILY_CHANCE = 0.00015D;
    private static final double SAND_CHUNK_LOAD_CHANCE = 0.0008D;

    // ======================== Zone Definition ========================

    private record ZoneRect(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        boolean contains(int x, int z) {
            return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
        }

        boolean containsSurfaceY(int y) {
            return y >= minY && y <= maxY;
        }
    }

    private static ZoneRect rect(int x1, int y1, int z1, int x2, int y2, int z2) {
        return new ZoneRect(
                Math.min(x1, x2),
                Math.min(y1, y2),
                Math.min(z1, z2),
                Math.max(x1, x2),
                Math.max(y1, y2),
                Math.max(z1, z2));
    }

    /**
     * All zones where artifact spots can spawn.
     * Uses same coordinate regions as ForageSpawnService + ArtifactDropService.
     */
    private record SpawnZone(String name, ZoneRect[] rects, int tileWidth, int tileHeight, SurfaceKind surface) {
        SpawnZone(String name, ZoneRect[] rects, int tileWidth, int tileHeight) {
            this(name, rects, tileWidth, tileHeight, SurfaceKind.YELLOW_DIRT);
        }
    }

    /** 表面类型：YELLOW_DIRT → 黄土；BEACH_SAND → 海滩沙子；DESERT_SAND → 沙漠沙子。 */
    private enum SurfaceKind { YELLOW_DIRT, BEACH_SAND, DESERT_SAND }

    // SDV locations mapped to MC coordinates
    private static final SpawnZone[] ZONES = {
            new SpawnZone("MainMap",
                new ZoneRect[]{ rect(200, 63, 79, -151, 91, -237) },
                352, 317),
            new SpawnZone("Beach",
                new ZoneRect[]{ rect(-4, 65, 77, 239, 57, 186) },
                244, 110, SurfaceKind.BEACH_SAND),
            new SpawnZone("Desert",
                new ZoneRect[]{ rect(-310, 53, -241, -158, 107, -113) },
                153, 129, SurfaceKind.DESERT_SAND),
    };

    // ======================== Daily Spawn (called from StardewTimeManager) ========================

    /**
     * Called once per day. Replicates SDV GameLocation.spawnObjects() artifact spot logic.
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
        if (!level.dimension().equals(ModDimensions.STARDEW_VALLEY)) {
            return DailySettlementWorkUnits.sequence("artifact_spot_daily", List.of(), () -> {});
        }

        List<SpawnZone> zones = List.of(ZONES);
        List<FarmArtifactDailyEntry> farmSnapshot = new ArrayList<>();
        for (com.stardew.craft.farm.FarmInstance farm
                : com.stardew.craft.farm.FarmInstanceRegistry.get().getAllFarms()) {
            BlockPos min = farm.getFarmBoundsMin();
            BlockPos max = farm.getFarmBoundsMax();
            farmSnapshot.add(new FarmArtifactDailyEntry(
                    farm.getOwnerUUID(),
                    rect(min.getX(), min.getY(), min.getZ(), max.getX(), max.getY(), max.getZ())));
        }
        farmSnapshot.sort(Comparator.comparing(entry -> entry.ownerId().toString()));

        long worldSeed = level.getSeed();
        int absoluteDay = context.absoluteDay();
        int season = context.season();
        List<DailySettlementWorkUnit> work = new ArrayList<>(zones.size() + farmSnapshot.size());
        for (SpawnZone zone : zones) {
            work.add(createArtifactZoneWorkUnit(
                    level, zone, season, worldSeed, absoluteDay));
        }
        for (FarmArtifactDailyEntry farm : farmSnapshot) {
            work.add(createFarmArtifactWorkUnit(
                    level, farm, season, worldSeed, absoluteDay));
        }
        return DailySettlementWorkUnits.sequence("artifact_spot_daily", work, () -> {});
    }

    private static DailySettlementWorkUnit createArtifactZoneWorkUnit(
            ServerLevel level,
            SpawnZone zone,
            int season,
            long worldSeed,
            int absoluteDay) {
        AtomicInteger existing = new AtomicInteger();
        List<DailySettlementWorkUnit> scans = new ArrayList<>(zone.rects.length);
        for (int index = 0; index < zone.rects.length; index++) {
            ZoneRect rect = zone.rects[index];
            scans.add(PublicAreaDailyWorkUnits.rectangle(
                    "artifact_scan_" + zone.name + "_" + index,
                    rect.minX, rect.minZ, rect.maxX, rect.maxZ,
                    (x, z) -> existing.addAndGet(processArtifactColumn(
                            level,
                            rect,
                            x,
                            z,
                            zone.surface,
                            zone.surface == SurfaceKind.YELLOW_DIRT,
                            worldSeed,
                            absoluteDay,
                            "artifact_remove_" + zone.name)),
                    () -> false,
                    () -> {}));
        }
        DailySettlementWorkUnit scan = DailySettlementWorkUnits.sequence(
                "artifact_scan_" + zone.name, scans, () -> {});
        DailySettlementWorkUnit spawn = DailySettlementWorkUnits.deferred(
                "artifact_spawn_" + zone.name,
                () -> createArtifactSpawnWorkUnit(
                        level, zone, existing.get(), season, worldSeed, absoluteDay));
        return DailySettlementWorkUnits.sequence(
                "artifact_zone_" + zone.name, List.of(scan, spawn), () -> {});
    }

    private static DailySettlementWorkUnit createFarmArtifactWorkUnit(
            ServerLevel level,
            FarmArtifactDailyEntry farm,
            int season,
            long worldSeed,
            int absoluteDay) {
        ZoneRect rect = farm.rect();
        AtomicInteger existing = new AtomicInteger();
        DailySettlementWorkUnit scan = PublicAreaDailyWorkUnits.rectangle(
                "artifact_farm_scan_" + farm.ownerId(),
                rect.minX, rect.minZ, rect.maxX, rect.maxZ,
                (x, z) -> existing.addAndGet(processArtifactColumn(
                        level,
                        rect,
                        x,
                        z,
                        SurfaceKind.YELLOW_DIRT,
                        false,
                        worldSeed,
                        absoluteDay,
                        "artifact_farm_remove_" + farm.ownerId())),
                () -> false,
                () -> {});
        DailySettlementWorkUnit spawn = DailySettlementWorkUnits.deferred(
                "artifact_farm_spawn_" + farm.ownerId(),
                () -> {
                    boolean overCap = existing.get() > MAX_SPOTS_FARM
                            && (season != 3 || existing.get() > MAX_SPOTS_WINTER);
                    if (overCap) {
                        return DailySettlementWorkUnits.sequence(
                                "artifact_farm_spawn_" + farm.ownerId(), List.of(), () -> {});
                    }
                    return artifactAttempts(
                            "artifact_farm_spawn_" + farm.ownerId(),
                            season,
                            worldSeed,
                            absoluteDay,
                            "artifact_farm_attempt",
                            stableUuid(farm.ownerId()),
                            random -> attemptFarmArtifactSpawn(level, rect, random));
                });
        return DailySettlementWorkUnits.sequence(
                "artifact_farm_" + farm.ownerId(), List.of(scan, spawn), () -> {});
    }

    private static DailySettlementWorkUnit createArtifactSpawnWorkUnit(
            ServerLevel level,
            SpawnZone zone,
            int existing,
            int season,
            long worldSeed,
            int absoluteDay) {
        if (isSandSurface(zone.surface)) {
            return createSandArtifactWorkUnit(
                    level, zone, existing, season, worldSeed, absoluteDay);
        }
        boolean overCap = existing > MAX_SPOTS_NON_FARM
                && (season != 3 || existing > MAX_SPOTS_WINTER);
        if (overCap) {
            return DailySettlementWorkUnits.sequence(
                    "artifact_spawn_" + zone.name, List.of(), () -> {});
        }
        return artifactAttempts(
                "artifact_spawn_" + zone.name,
                season,
                worldSeed,
                absoluteDay,
                "artifact_attempt_" + zone.name,
                stableStringId(zone.name),
                random -> attemptZoneArtifactSpawn(level, zone, random));
    }

    private static DailySettlementWorkUnit createSandArtifactWorkUnit(
            ServerLevel level,
            SpawnZone zone,
            int existing,
            int season,
            long worldSeed,
            int absoluteDay) {
        int cap = sandZoneDailyCap(zone, season);
        if (existing >= cap) {
            return DailySettlementWorkUnits.sequence(
                    "artifact_sand_spawn_" + zone.name, List.of(), () -> {});
        }
        int remaining = cap - existing;
        double chance = zone.surface == SurfaceKind.DESERT_SAND
                ? DESERT_SAND_DAILY_CHANCE
                : BEACH_SAND_DAILY_CHANCE;
        AtomicInteger placed = new AtomicInteger();
        List<DailySettlementWorkUnit> chanceScans = new ArrayList<>(zone.rects.length);
        for (int index = 0; index < zone.rects.length; index++) {
            ZoneRect rect = zone.rects[index];
            chanceScans.add(PublicAreaDailyWorkUnits.cappedRectangle(
                    "artifact_sand_chance_" + zone.name + "_" + index,
                    rect.minX, rect.minZ, rect.maxX, rect.maxZ,
                    remaining,
                    (x, z) -> {
                        RandomSource random = DailySettlementRandom.forPosition(
                                worldSeed,
                                absoluteDay,
                                "artifact_sand_chance_" + zone.name,
                                new BlockPos(x, 0, z));
                        if (random.nextDouble() >= chance) return false;
                        if (!level.hasChunk(x >> 4, z >> 4)) return false;
                        if (chunkAlreadyHasSpot(level, x >> 4, z >> 4, zone.surface)) return false;
                        if (tryPlaceArtifactSpot(level, x, z, zone.surface)) {
                            placed.incrementAndGet();
                            return true;
                        }
                        return false;
                    },
                    () -> placed.get() >= remaining,
                    () -> {}));
        }
        DailySettlementWorkUnit chanceScan = DailySettlementWorkUnits.sequence(
                "artifact_sand_chance_" + zone.name, chanceScans, () -> {});

        AtomicReference<BlockPos> fallback = new AtomicReference<>();
        AtomicLong bestScore = new AtomicLong(-1L);
        List<DailySettlementWorkUnit> fallbackScans = new ArrayList<>(zone.rects.length);
        for (int index = 0; index < zone.rects.length; index++) {
            ZoneRect rect = zone.rects[index];
            fallbackScans.add(PublicAreaDailyWorkUnits.rectangle(
                    "artifact_sand_fallback_" + zone.name + "_" + index,
                    rect.minX, rect.minZ, rect.maxX, rect.maxZ,
                    (x, z) -> {
                        if (!level.hasChunk(x >> 4, z >> 4)) return;
                        if (!canSpawnArtifactSpot(level, x, z, zone.surface)) return;
                        long score = DailySettlementRandom.forPosition(
                                worldSeed,
                                absoluteDay,
                                "artifact_sand_fallback_" + zone.name,
                                new BlockPos(x, 0, z)).nextLong();
                        if (Long.compareUnsigned(score, bestScore.get()) < 0) {
                            bestScore.set(score);
                            fallback.set(new BlockPos(x, 0, z));
                        }
                    },
                    () -> placed.get() > 0 || existing > 0,
                    () -> {}));
        }
        DailySettlementWorkUnit fallbackScan = DailySettlementWorkUnits.sequence(
                "artifact_sand_fallback_" + zone.name, fallbackScans, () -> {});
        DailySettlementWorkUnit fallbackPlace = DailySettlementWorkUnits.atomic(
                "artifact_sand_fallback_place_" + zone.name,
                () -> {
                    BlockPos chosen = fallback.get();
                    if (placed.get() == 0 && existing == 0 && chosen != null
                            && tryPlaceArtifactSpot(level, chosen.getX(), chosen.getZ(), zone.surface)) {
                        placed.incrementAndGet();
                    }
                },
                () -> {});
        return DailySettlementWorkUnits.sequence(
                "artifact_sand_spawn_" + zone.name,
                List.of(chanceScan, fallbackScan, fallbackPlace),
                () -> {});
    }

    private static int processArtifactColumn(
            ServerLevel level,
            ZoneRect rect,
            int x,
            int z,
            SurfaceKind surface,
            boolean revertFarmland,
            long worldSeed,
            int absoluteDay,
            String randomSubsystem) {
        if (!level.hasChunk(x >> 4, z >> 4)) return 0;
        int surfaceY = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
        BlockPos pos = new BlockPos(x, surfaceY, z);
        if (revertFarmland
                && level.getBlockState(pos).is(net.minecraft.world.level.block.Blocks.FARMLAND)
                && level.getBlockState(pos.above()).isAir()) {
            level.setBlock(pos, ModBlocks.YELLOW_DIRT.get().defaultBlockState(), Block.UPDATE_ALL);
        }
        Block spotBlock = spotBlockFor(surface);
        BlockState state = level.getBlockState(pos);
        if (state.is(spotBlock)) {
            RandomSource random = DailySettlementRandom.forPosition(
                    worldSeed, absoluteDay, randomSubsystem, new BlockPos(x, 0, z));
            if (random.nextDouble() < 0.15D) {
                level.setBlock(pos, underlyingStateFor(state, surface), Block.UPDATE_ALL);
                return 0;
            }
            return 1;
        }
        return 0;
    }

    private static void attemptZoneArtifactSpawn(
            ServerLevel level, SpawnZone zone, RandomSource random) {
        ZoneRect rect = zone.rects[random.nextInt(zone.rects.length)];
        int x = rect.minX + random.nextInt(rect.maxX - rect.minX + 1);
        int z = rect.minZ + random.nextInt(rect.maxZ - rect.minZ + 1);
        if (!level.hasChunk(x >> 4, z >> 4)) return;
        tryPlaceArtifactSpot(level, x, z, zone.surface);
    }

    private static void attemptFarmArtifactSpawn(
            ServerLevel level, ZoneRect rect, RandomSource random) {
        int x = rect.minX + random.nextInt(rect.maxX - rect.minX + 1);
        int z = rect.minZ + random.nextInt(rect.maxZ - rect.minZ + 1);
        if (!level.hasChunk(x >> 4, z >> 4)) return;
        if (!canSpawnArtifactSpotInRect(level, x, z, SurfaceKind.YELLOW_DIRT, rect)) return;
        int surfaceY = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
        BlockPos pos = new BlockPos(x, surfaceY, z);
        level.setBlock(
                pos,
                spotStateFor(level.getBlockState(pos), SurfaceKind.YELLOW_DIRT),
                Block.UPDATE_ALL);
    }

    private static DailySettlementWorkUnit artifactAttempts(
            String name,
            int season,
            long worldSeed,
            int absoluteDay,
            String subsystem,
            long stableId,
            ArtifactAttempt operation) {
        return PublicAreaDailyWorkUnits.decayingAttempts(
                name,
                season == 3,
                (cursor, chance) -> {
                    RandomSource random = DailySettlementRandom.forId(
                            worldSeed, absoluteDay, subsystem, stableId ^ cursor);
                    if (random.nextDouble() >= chance) {
                        return false;
                    }
                    operation.attempt(random);
                    return true;
                },
                () -> {});
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

    @FunctionalInterface
    private interface ArtifactAttempt {
        void attempt(RandomSource random);
    }

    private record FarmArtifactDailyEntry(UUID ownerId, ZoneRect rect) {
    }

    // ======================== Chunk Load Spawn ========================

    /**
     * 区块加载时按低概率撒远古斑点，是最稳定 / 多人友好的兜底机制。
     * 关键约束：每个区块最多只能有 1 个斑点（chunkAlreadyHasSpot 检查），
     * 这样多人服务器即便频繁加载也不会无限堆积。
     */
    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) return;
        if (!serverLevel.dimension().equals(ModDimensions.STARDEW_VALLEY)) return;
        if (!(event.getChunk() instanceof LevelChunk chunk)) return;

        // Defer artifact-spot spawning to the next server tick to avoid
        // recursive chunk loading (getHeight / setBlock inside a ChunkEvent.Load
        // callback can deadlock the server thread).
        final int chunkX = chunk.getPos().x;
        final int chunkZ = chunk.getPos().z;
        final int savedChunkX = chunk.getPos().getMinBlockX();
        final int savedChunkZ = chunk.getPos().getMinBlockZ();
        serverLevel.getServer().tell(new net.minecraft.server.TickTask(
            serverLevel.getServer().getTickCount() + 1, () -> {
                RandomSource random = serverLevel.getRandom();
                // 多人友好硬上限：每区块最多 1 个斑点。先扫一次，已经有就直接 return。
                if (chunkHasAnySpot(serverLevel, chunkX, chunkZ)) {
                    return;
                }
                // 削率：原 0.00067 → 0.0002（约每 78 个区块期望生成一个）
                for (int dx = 0; dx < 16; dx++) {
                    for (int dz = 0; dz < 16; dz++) {
                        int x = savedChunkX + dx;
                        int z = savedChunkZ + dz;
                        SurfaceKind surface = surfaceKindAt(serverLevel, x, z);
                        if (surface == null) continue;
                        double chunkLoadChance = isSandSurface(surface) ? SAND_CHUNK_LOAD_CHANCE : 0.0002D;
                        if (random.nextDouble() >= chunkLoadChance) continue;
                        int surfaceY = serverLevel.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
                        BlockPos pos = new BlockPos(x, surfaceY, z);
                        if (canSpawnArtifactSpot(serverLevel, x, z, surface)) {
                            serverLevel.setBlock(pos, spotStateFor(serverLevel.getBlockState(pos), surface),
                                    Block.UPDATE_ALL);
                            return;
                        }
                    }
                }
            }));
    }

    // ======================== Helpers ========================

    /**
     * 该区块表面是否已经有一个该表面类型的远古斑点（多人友好的硬上限）。
     * 只扫 16×16 高度图顶部一格，开销可控。
     */
    private static boolean chunkAlreadyHasSpot(ServerLevel level, int chunkX, int chunkZ, SurfaceKind surface) {
        Block spotBlock = spotBlockFor(surface);
        int baseX = chunkX << 4;
        int baseZ = chunkZ << 4;
        for (int dx = 0; dx < 16; dx++) {
            for (int dz = 0; dz < 16; dz++) {
                int x = baseX + dx;
                int z = baseZ + dz;
                int surfaceY = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
                if (level.getBlockState(new BlockPos(x, surfaceY, z)).is(spotBlock)) return true;
            }
        }
        return false;
    }

    private static boolean chunkHasAnySpot(ServerLevel level, int chunkX, int chunkZ) {
        int baseX = chunkX << 4;
        int baseZ = chunkZ << 4;
        for (int dx = 0; dx < 16; dx++) {
            for (int dz = 0; dz < 16; dz++) {
                int x = baseX + dx;
                int z = baseZ + dz;
                int surfaceY = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
                BlockState state = level.getBlockState(new BlockPos(x, surfaceY, z));
                if (state.is(ModBlocks.ARTIFACT_SPOT_DIRT.get())
                        || state.is(ModBlocks.DESERT_ARTIFACT_SPOT.get())
                        || state.is(ModBlocks.BEACH_ARTIFACT_SPOT.get())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static SurfaceKind surfaceKindAt(ServerLevel level, int x, int z) {
        int surfaceY = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
        for (SpawnZone zone : ZONES) {
            for (ZoneRect rect : zone.rects) {
                if (rect.contains(x, z) && rect.containsSurfaceY(surfaceY)) {
                    return zone.surface;
                }
            }
        }
        return null;
    }

    /**
     * Check if an artifact spot can spawn at (x, z).
     * Conditions: surface block matches the zone's surface kind, block above is air, can see sky.
     */
    private static boolean canSpawnArtifactSpot(ServerLevel level, int x, int z, SurfaceKind surface) {
        int surfaceY = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
        BlockPos pos = new BlockPos(x, surfaceY, z);
        BlockState state = level.getBlockState(pos);

        if (!isSurfaceYInZone(x, surfaceY, z, surface)) return false;
        if (!matchesSurface(state, surface)) return false;

        BlockPos above = pos.above();
        return level.getBlockState(above).isAir() && level.canSeeSky(above);
    }

    private static boolean canSpawnArtifactSpotInRect(ServerLevel level, int x, int z, SurfaceKind surface, ZoneRect rect) {
        int surfaceY = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
        if (!rect.contains(x, z) || !rect.containsSurfaceY(surfaceY)) {
            return false;
        }
        BlockPos pos = new BlockPos(x, surfaceY, z);
        if (!matchesSurface(level.getBlockState(pos), surface)) {
            return false;
        }
        BlockPos above = pos.above();
        return level.getBlockState(above).isAir() && level.canSeeSky(above);
    }

    private static boolean isSurfaceYInZone(int x, int y, int z, SurfaceKind surface) {
        for (SpawnZone zone : ZONES) {
            if (zone.surface != surface) continue;
            for (ZoneRect rect : zone.rects) {
                if (rect.contains(x, z) && rect.containsSurfaceY(y)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** 判断表面是否为该区域可生成远古斑点的原始方块。 */
    private static boolean matchesSurface(BlockState state, SurfaceKind surface) {
        return switch (surface) {
            case YELLOW_DIRT -> state.is(ModBlocks.YELLOW_DIRT.get());
            case BEACH_SAND, DESERT_SAND -> state.is(net.minecraft.world.level.block.Blocks.SAND);
        };
    }

    private static boolean isSandSurface(SurfaceKind surface) {
        return surface == SurfaceKind.BEACH_SAND || surface == SurfaceKind.DESERT_SAND;
    }

    private static int sandZoneDailyCap(SpawnZone zone, int season) {
        if (zone.surface == SurfaceKind.DESERT_SAND) {
            return DESERT_DAILY_CAP;
        }
        return season == 3 ? MAX_SPOTS_WINTER + 1 : MAX_SPOTS_NON_FARM + 1;
    }

    private static boolean tryPlaceArtifactSpot(ServerLevel level, int x, int z, SurfaceKind surface) {
        if (!canSpawnArtifactSpot(level, x, z, surface)) return false;
        int surfaceY = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
        BlockPos pos = new BlockPos(x, surfaceY, z);
        level.setBlock(pos, spotStateFor(level.getBlockState(pos), surface), Block.UPDATE_ALL);
        return true;
    }

    /** 返回该表面类型对应的远古斑点方块。 */
    private static Block spotBlockFor(SurfaceKind surface) {
        return switch (surface) {
            case YELLOW_DIRT -> ModBlocks.ARTIFACT_SPOT_DIRT.get();
            case BEACH_SAND -> ModBlocks.BEACH_ARTIFACT_SPOT.get();
            case DESERT_SAND -> ModBlocks.DESERT_ARTIFACT_SPOT.get();
        };
    }

    private static BlockState spotStateFor(BlockState underlyingState, SurfaceKind surface) {
        Block block = spotBlockFor(surface);
        if (block instanceof ArtifactSpotBlock artifactSpot) {
            return artifactSpot.stateForUnderlying(underlyingState);
        }
        return block.defaultBlockState();
    }

    /** 返回该表面类型被锄头锄后应还原为哪种原始方块。 */
    private static Block underlyingBlockFor(SurfaceKind surface) {
        return switch (surface) {
            case YELLOW_DIRT -> ModBlocks.YELLOW_DIRT.get();
            case BEACH_SAND, DESERT_SAND -> net.minecraft.world.level.block.Blocks.SAND;
        };
    }

    private static BlockState underlyingStateFor(BlockState state, SurfaceKind surface) {
        if (state.getBlock() instanceof ArtifactSpotBlock artifactSpot) {
            return artifactSpot.resolveUnderlyingState(state);
        }
        return underlyingBlockFor(surface).defaultBlockState();
    }

    // ======================== First-Day Initial Spawn ========================

    /**
     * Called on first entry into the Stardew dimension. Ensures artifact spots exist on Day 1.
     * Uses SavedData to guarantee it only runs once per world.
     */
    public static void ensureInitialSpawn(ServerLevel level, int season) {
        if (!level.dimension().equals(ModDimensions.STARDEW_VALLEY)) return;

        ArtifactInitData data = level.getDataStorage().computeIfAbsent(
                ArtifactInitData.factory(), INIT_DATA_ID);
        if (!data.isInitialized()) {
            onNewDay(level, season);
            data.markInitialized();
            data.markDesertInitialized();
            return;
        }
        // 老存档补扫：沙漠远古斑点是后加的，需要一次性 bbox 扫描
        if (!data.isDesertInitialized()) {
            RandomSource random = level.getRandom();
            for (SpawnZone zone : ZONES) {
                if (zone.surface != SurfaceKind.DESERT_SAND) continue;
                // 削后的首次补扫概率：原 0.0008 → 0.0003，配合 DESERT_DAILY_CAP 与每区块上限
                final double perBlockChance = 0.0003;
                int placed = 0;
                outer:
                for (ZoneRect rect : zone.rects) {
                    for (int x = rect.minX; x <= rect.maxX; x++) {
                        for (int z = rect.minZ; z <= rect.maxZ; z++) {
                            if (placed >= DESERT_DAILY_CAP) break outer;
                            if (!level.hasChunk(x >> 4, z >> 4)) continue;
                            if (random.nextDouble() >= perBlockChance) continue;
                            if (chunkAlreadyHasSpot(level, x >> 4, z >> 4, zone.surface)) continue;
                            if (canSpawnArtifactSpot(level, x, z, zone.surface)) {
                                int surfaceY = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
                                BlockPos pos = new BlockPos(x, surfaceY, z);
                                level.setBlock(pos, spotStateFor(level.getBlockState(pos), zone.surface), Block.UPDATE_ALL);
                                placed++;
                            }
                        }
                    }
                }
            }
            data.markDesertInitialized();
        }
    }

    public static class ArtifactInitData extends SavedData {
        private boolean initialized;
        private boolean desertInitialized;

        public ArtifactInitData() {}

        private ArtifactInitData(CompoundTag tag) {
            this.initialized = tag.getBoolean("Initialized");
            this.desertInitialized = tag.getBoolean("DesertInitialized");
        }

        public boolean isInitialized() { return initialized; }
        public boolean isDesertInitialized() { return desertInitialized; }

        public void markInitialized() {
            this.initialized = true;
            setDirty();
        }

        public void markDesertInitialized() {
            this.desertInitialized = true;
            setDirty();
        }

        @Override
        @Nonnull
        public CompoundTag save(@Nonnull CompoundTag tag, @Nonnull HolderLookup.Provider registries) {
            tag.putBoolean("Initialized", initialized);
            tag.putBoolean("DesertInitialized", desertInitialized);
            return tag;
        }

        public static SavedData.Factory<ArtifactInitData> factory() {
            return new SavedData.Factory<>(ArtifactInitData::new, (tag, provider) -> new ArtifactInitData(tag));
        }
    }
}
