package com.stardew.craft.farm;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.api.v1.farm.StardewFarmLayout;
import com.stardew.craft.api.v1.internal.farm.StardewFarmLayoutRegistry;
import com.stardew.craft.api.v1.farm.StardewFarmInitializationSteps;
import com.stardew.craft.api.v1.farm.StardewFarmLayoutMigrations;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.decor.MapDecorStaticBlock;
import com.stardew.craft.block.decor.FarmTwigBlock;
import com.stardew.craft.block.decor.ResourceClumpBlock;
import com.stardew.craft.block.nature.PastureGrassBlock;
import com.stardew.craft.block.nature.WildWeedsBlock;
import com.stardew.craft.time.StardewTimeManager;
import com.stardew.craft.tree.WildTrees;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

/**
 * 玩家个人农场实例的初始化器。
 * 放置 schematic → 设置生物群系 → 放置图腾柱 → 温室 → 原版地图密度的碎片 → 出口实体。
 * <p>
 * 初始碎片密度来自原版各农场 TMX 的 Paths 层：13–15 杂草，16–17 石头，
 * 18 树枝，19–21 大型障碍物，22 牧草，23 幼树。Minecraft 农场的地表几何不同，
 * 所以保留每张地图的密度与树种比例，但在对应的自然裸地上重新分布。
 */
@SuppressWarnings("null")
public class FarmInstanceInitializer {

    private static final int CLEAR_RADIUS = 5;
    private static final int TREE_MIN_DIST = 4;

    private record InitialDebrisProfile(
            int treePermille,
            int stonePermille,
            int weedPermille,
            int twigPermille,
            int grassPermille,
            int saplingPermille,
            int oakWeight,
            int mapleWeight,
            int pineWeight,
            int largeLogs,
            int largeBoulders,
            int largeStumps
    ) {
        private static final InitialDebrisProfile STANDARD = new InitialDebrisProfile(
                19, 83, 95, 28, 75, 4,
                28, 29, 41,
                6, 14, 19);
        private static final InitialDebrisProfile FOREST = new InitialDebrisProfile(
                9, 75, 49, 13, 28, 3,
                11, 10, 28,
                9, 9, 12);
        private static final InitialDebrisProfile RIVERLAND = new InitialDebrisProfile(
                8, 74, 44, 15, 38, 2,
                9, 11, 23,
                1, 7, 12);
    }

    /**
     * 初始化指定玩家的农场实例。
     */
    public static boolean initializeFarm(ServerLevel level, FarmInstance farm) {
        if (farm.isInitialized()) {
            StardewCraft.LOGGER.warn("[FARM_INIT] Farm for {} already initialized", farm.getOwnerName());
            StardewFarmLayoutMigrations.runPending(level, farm.getOwnerUUID());
            StardewFarmInitializationSteps.runPending(level, farm.getOwnerUUID());
            return true;
        }

        StardewFarmLayout layout = farm.getFarmLayout();
        if (layout == null) {
            StardewCraft.LOGGER.error(
                    "[FARM_INIT] No layout data for farm type {}",
                    farm.getFarmLayoutId());
            return false;
        }

        BlockPos origin = farm.getOrigin();
        StardewCraft.LOGGER.info("[FARM_INIT] Initializing {} farm for {} at origin {}",
                farm.getFarmLayoutId(), farm.getOwnerName(), origin);

        // 1. 预加载区块
        preloadFarmChunks(level, farm);

        // 2. 放置 schematic（地形）
        if (!placeSchematic(level, farm)) return false;
        if (farm.getFarmLayoutId().getNamespace().equals(StardewCraft.MODID)) {
            int replaced = FarmSubsoil.replaceBuriedDirt(level, origin, origin.offset(layout.boundsMax()));
            StardewCraft.LOGGER.info("[FARM_INIT] Replaced {} buried dirt blocks with hard soil", replaced);
        }

        // 2.5 在 schematic 底面正下方铺一层基岩，防止掉出世界
        placeBedrockFloor(level, farm, layout);

        // 3. 设置生物群系（非 default 的农场类型）
        if (layout.biomeId() != null) {
            setFarmBiome(level, farm, layout.biomeId());
        }

        // 4. 放置农场图腾柱（朝西）
        placeFarmTotemPole(level, farm);

        // 5. 放置温室（门口朝西，CW90 旋转）
        com.stardew.craft.greenhouse.GreenhouseManager.get(level).ensurePlacedForPlayer(level, farm.getOwnerUUID());

        // 6. 概率化生成自然碎片
        spawnNaturalDebris(level, farm);

        // 7. 放置 3 个出口交互实体
        spawnExitPortals(level, farm, layout);

        // 7.5 放置农场洞穴系统（室外墙 + 室外传送方块 + 室内结构）
        placeFarmCaveSystem(level, farm, layout);

        // 8. 河边农场特殊：送熏鱼机
        if (farm.getFarmLayoutId().equals(
                StardewFarmLayoutRegistry.builtinId(FarmType.RIVERLAND))) {
            giveStarterItem(level, farm, ModBlocks.FISH_SMOKER.get().asItem());
        }

        farm.markInitialized();
        FarmInstanceRegistry.get().setDirty();
        StardewFarmLayoutMigrations.runPending(level, farm.getOwnerUUID());
        StardewFarmInitializationSteps.runPending(level, farm.getOwnerUUID());
        StardewCraft.LOGGER.info("[FARM_INIT] Farm initialization complete for {}", farm.getOwnerName());
        return true;
    }

    // ══════════════════════════════════════════
    //  Schematic 放置
    // ══════════════════════════════════════════

    private static boolean placeSchematic(ServerLevel level, FarmInstance farm) {
        ResourceLocation path = farm.getFarmLayout().schematic();
        BlockPos origin = farm.getOrigin();
        boolean result = com.stardew.craft.mining.StructureLoader.loadAndPlaceWithResult(level, path, origin);
        if (!result) {
            StardewCraft.LOGGER.error("[FARM_INIT] Failed to place schematic {} at {}", path, origin);
        } else {
            StardewCraft.LOGGER.info("[FARM_INIT] Placed schematic {} at {}", path, origin);
        }
        return result;
    }

    /**
     * 在农场 schematic 底面正下方铺一整层基岩，防止玩家掉出世界。
     * Y = origin.getY() - 1，覆盖 schemWidth × schemLength 的完整区域。
     */
    private static void placeBedrockFloor(
            ServerLevel level,
            FarmInstance farm,
            StardewFarmLayout layout
    ) {
        BlockPos origin = farm.getOrigin();
        int bedrockY = origin.getY() - 1;
        int startX = origin.getX();
        int startZ = origin.getZ();
        int endX = startX + layout.width();
        int endZ = startZ + layout.length();
        net.minecraft.world.level.block.state.BlockState bedrock = net.minecraft.world.level.block.Blocks.BEDROCK.defaultBlockState();

        for (int x = startX; x < endX; x++) {
            for (int z = startZ; z < endZ; z++) {
                level.setBlock(new BlockPos(x, bedrockY, z), bedrock, 2);
            }
        }
        StardewCraft.LOGGER.info("[FARM_INIT] Bedrock floor placed at Y={} ({} x {} blocks)",
                bedrockY, layout.width(), layout.length());
    }

    // ══════════════════════════════════════════
    //  生物群系设置
    // ══════════════════════════════════════════

    private static void setFarmBiome(ServerLevel level, FarmInstance farm, String biomeId) {
        ResourceLocation biomeLocation = biomeId.indexOf(':') >= 0
                ? ResourceLocation.tryParse(biomeId)
                : ResourceLocation.tryBuild(
                        StardewCraft.MODID, biomeId);
        if (biomeLocation == null) {
            StardewCraft.LOGGER.error(
                    "[FARM_INIT] Invalid biome ID {}", biomeId);
            return;
        }
        ResourceKey<Biome> biomeKey = ResourceKey.create(Registries.BIOME,
                biomeLocation);
        Holder<Biome> biomeHolder;
        try {
            biomeHolder = level.registryAccess()
                    .registryOrThrow(Registries.BIOME)
                    .getHolderOrThrow(biomeKey);
        } catch (Exception e) {
            StardewCraft.LOGGER.error("[FARM_INIT] Biome {} not found", biomeId);
            return;
        }

        BlockPos min = farm.getFarmBoundsMin();
        BlockPos max = farm.getFarmBoundsMax();
        int minCX = min.getX() >> 4, maxCX = max.getX() >> 4;
        int minCZ = min.getZ() >> 4, maxCZ = max.getZ() >> 4;

        int modified = 0;
        for (int cx = minCX; cx <= maxCX; cx++) {
            for (int cz = minCZ; cz <= maxCZ; cz++) {
                LevelChunk chunk = level.getChunk(cx, cz);
                boolean changed = false;
                for (int si = 0; si < chunk.getSections().length; si++) {
                    LevelChunkSection section = chunk.getSections()[si];
                    if (section == null) continue;
                    @SuppressWarnings("unchecked")
                    net.minecraft.world.level.chunk.PalettedContainer<Holder<Biome>> biomes =
                            (net.minecraft.world.level.chunk.PalettedContainer<Holder<Biome>>)
                                    (Object) section.getBiomes();
                    for (int bx = 0; bx < 4; bx++) {
                        for (int by = 0; by < 4; by++) {
                            for (int bz = 0; bz < 4; bz++) {
                                biomes.set(bx, by, bz, biomeHolder);
                                changed = true;
                            }
                        }
                    }
                }
                if (changed) {
                    chunk.setUnsaved(true);
                    modified++;
                }
            }
        }
        StardewCraft.LOGGER.info("[FARM_INIT] Set biome {} for {} chunks", biomeId, modified);
    }

    // ══════════════════════════════════════════
    //  自然碎片
    // ══════════════════════════════════════════

    private static void spawnNaturalDebris(ServerLevel level, FarmInstance farm) {
        RandomSource random = level.getRandom();
        BlockPos boundsMin = farm.getFarmBoundsMin();
        BlockPos boundsMax = farm.getFarmBoundsMax();
        BlockPos spawnPos = farm.getSpawnPoint();
        BlockPos greenhousePos = farm.getGreenhousePos();
        InitialDebrisProfile profile = initialDebrisProfile(farm);
        int season = StardewTimeManager.get().getCurrentSeason();

        int largeLogs = spawnInitialClumps(level, farm, ModBlocks.HOLLOW_LOG.get(),
                profile.largeLogs(), random);
        int largeBoulders = spawnInitialClumps(level, farm, ModBlocks.LARGE_BOULDER.get(),
                profile.largeBoulders(), random);
        int largeStumps = spawnInitialClumps(level, farm, ModBlocks.LARGE_STUMP.get(),
                profile.largeStumps(), random);

        int trees = 0, stones = 0, weeds = 0, twigs = 0, grass = 0, saplings = 0;

        for (int x = boundsMin.getX(); x <= boundsMax.getX(); x++) {
            for (int z = boundsMin.getZ(); z <= boundsMax.getZ(); z++) {
                if (isNearProtected(x, z, spawnPos, greenhousePos)) continue;

                FarmDebrisPlacementRules.Surface surface =
                        FarmDebrisPlacementRules.findBareSurface(level, farm, x, z);
                if (surface == null) continue;

                BlockPos placePos = surface.place();
                boolean onGrass = surface.grass();
                int roll = random.nextInt(1000);
                int cumulative = 0;

                // Paths 9/10/11: mature oak/maple/pine, using this layout's source ratio.
                cumulative += profile.treePermille();
                if (roll < cumulative) {
                    if (!hasNearbyInitialTree(level, placePos, TREE_MIN_DIST)) {
                        WildTrees.Def chosen = pickInitialTree(random, profile);
                        if (com.stardew.craft.tree.prefab.PrefabTreeManager.tryPlaceRandomVariant(level, placePos, chosen)) {
                            trees++;
                        }
                    }
                    continue;
                }

                // Paths 16/17: loose stones occur on the diggable dirt portion.
                if (!onGrass) {
                    cumulative += profile.stonePermille();
                    if (roll < cumulative) {
                        Block[] stoneBlocks = {ModBlocks.MINE_STONE_343.get(), ModBlocks.MINE_STONE_450.get()};
                        level.setBlock(placePos, stoneBlocks[random.nextInt(stoneBlocks.length)].defaultBlockState(), 3);
                        stones++;
                        continue;
                    }
                }

                // Paths 13/14/15: seasonal weeds.
                cumulative += profile.weedPermille();
                if (roll < cumulative) {
                    int variant = random.nextInt(3);
                    BlockState state = ModBlocks.WILD_WEEDS.get().defaultBlockState()
                            .setValue(WildWeedsBlock.SEASON, Math.max(0, Math.min(3, season)))
                            .setValue(WildWeedsBlock.VARIANT, variant);
                    level.setBlock(placePos, state, 3);
                    weeds++;
                    continue;
                }

                // Path 18: the two original farm twig variants.
                cumulative += profile.twigPermille();
                if (roll < cumulative) {
                    level.setBlock(placePos, initialTwigState(random), 3);
                    twigs++;
                    continue;
                }

                // Path 22: pasture grass.
                cumulative += profile.grassPermille();
                if (roll < cumulative) {
                    BlockState state = ModBlocks.PASTURE_GRASS.get().defaultBlockState()
                            .setValue(PastureGrassBlock.VARIANT,
                                    random.nextInt(PastureGrassBlock.VISUAL_VARIANT_COUNT));
                    level.setBlock(placePos, state, 3);
                    grass++;
                    continue;
                }

                // Path 23: a young oak/maple/pine on diggable dirt.
                if (!onGrass) {
                    cumulative += profile.saplingPermille();
                    if (roll < cumulative) {
                        WildTrees.Def def = pickInitialTree(random, profile);
                        Block sapling = random.nextBoolean() ? def.sapling0().get() : def.sapling1().get();
                        BlockState state = sapling.defaultBlockState();
                        if (state.canSurvive(level, placePos)) {
                            level.setBlock(placePos, state, 3);
                            saplings++;
                        }
                    }
                }
            }
        }

        StardewCraft.LOGGER.info(
                "[FARM_INIT] Debris: trees={}, stones={}, weeds={}, twigs={}, grass={}, saplings={}, largeLogs={}, largeBoulders={}, largeStumps={}",
                trees, stones, weeds, twigs, grass, saplings,
                largeLogs, largeBoulders, largeStumps);
    }

    private static InitialDebrisProfile initialDebrisProfile(FarmInstance farm) {
        if (farm.getFarmLayoutId().equals(
                StardewFarmLayoutRegistry.builtinId(FarmType.FOREST))) {
            return InitialDebrisProfile.FOREST;
        }
        if (farm.getFarmLayoutId().equals(
                StardewFarmLayoutRegistry.builtinId(FarmType.RIVERLAND))) {
            return InitialDebrisProfile.RIVERLAND;
        }
        return InitialDebrisProfile.STANDARD;
    }

    private static WildTrees.Def pickInitialTree(RandomSource random, InitialDebrisProfile profile) {
        WildTrees.Def[] trees = {WildTrees.OAK, WildTrees.MAPLE, WildTrees.PINE};
        int[] weights = {profile.oakWeight(), profile.mapleWeight(), profile.pineWeight()};
        return pickWeighted(random, trees, weights);
    }

    private static BlockState initialTwigState(RandomSource random) {
        Direction[] facings = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
        return ModBlocks.FARM_TWIG.get().defaultBlockState()
                .setValue(FarmTwigBlock.VARIANT, random.nextInt(2))
                .setValue(FarmTwigBlock.FACING, facings[random.nextInt(facings.length)]);
    }

    private static int spawnInitialClumps(ServerLevel level, FarmInstance farm, Block block,
                                          int requested, RandomSource random) {
        if (!(block instanceof ResourceClumpBlock clump) || requested <= 0) {
            return 0;
        }
        BlockPos min = farm.getFarmBoundsMin();
        BlockPos max = farm.getFarmBoundsMax();
        int placed = 0;
        int attempts = Math.max(64, requested * 80);
        for (int attempt = 0; attempt < attempts && placed < requested; attempt++) {
            int x = min.getX() + random.nextInt(max.getX() - min.getX() + 1);
            int z = min.getZ() + random.nextInt(max.getZ() - min.getZ() + 1);
            if (isNearProtected(x, z, farm.getSpawnPoint(), farm.getGreenhousePos(), 2)) {
                continue;
            }
            FarmDebrisPlacementRules.Surface surface =
                    FarmDebrisPlacementRules.findBareSurface(level, farm, x, z);
            if (surface == null || !canPlaceInitialClump(level, farm, surface.place())) {
                continue;
            }
            Direction facing = Direction.Plane.HORIZONTAL.getRandomDirection(random);
            BlockState state = block.defaultBlockState()
                    .setValue(MapDecorStaticBlock.PART, MapDecorStaticBlock.Part.MAIN)
                    .setValue(MapDecorStaticBlock.FACING, facing);
            if (!level.setBlock(surface.place(), state, Block.UPDATE_ALL)) {
                continue;
            }
            if (!clump.placeExtensions(level, surface.place(), state)) {
                level.removeBlock(surface.place(), false);
                continue;
            }
            placed++;
        }
        return placed;
    }

    /** Resource clumps occupy a 3x3 footprint and two vertical cells. */
    private static boolean canPlaceInitialClump(
            ServerLevel level,
            FarmInstance farm,
            BlockPos main
    ) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos bottom = main.offset(dx, 0, dz);
                BlockPos top = bottom.above();
                if (!farm.contains(bottom) || !farm.contains(top)
                        || !FarmDebrisPlacementRules.isCompletelyOpen(level, bottom)
                        || !FarmDebrisPlacementRules.isCompletelyOpen(level, top)) {
                    return false;
                }
                FarmDebrisPlacementRules.Surface surface =
                        FarmDebrisPlacementRules.findBareSurface(
                                level, farm, bottom.getX(), bottom.getZ());
                if (surface == null || !surface.place().equals(bottom)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean isNearProtected(int x, int z, BlockPos spawn, BlockPos greenhouse) {
        return isNearProtected(x, z, spawn, greenhouse, 0);
    }

    private static boolean isNearProtected(int x, int z, BlockPos spawn, BlockPos greenhouse, int margin) {
        if (Math.abs(x - spawn.getX()) <= CLEAR_RADIUS + margin
                && Math.abs(z - spawn.getZ()) <= CLEAR_RADIUS + margin) return true;
        if (x >= greenhouse.getX() - 2 - margin && x <= greenhouse.getX() + 19 + margin
                && z >= greenhouse.getZ() - 2 - margin && z <= greenhouse.getZ() + 19 + margin) return true;
        return false;
    }

    private static boolean hasNearbyInitialTree(ServerLevel level, BlockPos pos, int radius) {
        for (BlockPos nearby : BlockPos.betweenClosed(
                pos.offset(-radius, 0, -radius), pos.offset(radius, 1, radius))) {
            if (WildTrees.findByAnyPart(level.getBlockState(nearby)) != null) {
                return true;
            }
        }
        return false;
    }

    private static <T> T pickWeighted(RandomSource random, T[] items, int[] weights) {
        int total = 0;
        for (int w : weights) total += w;
        int roll = random.nextInt(total);
        int cumulative = 0;
        for (int i = 0; i < items.length; i++) {
            cumulative += weights[i];
            if (roll < cumulative) return items[i];
        }
        return items[items.length - 1];
    }

    // ══════════════════════════════════════════
    //  图腾柱（朝西）
    // ══════════════════════════════════════════

    private static void placeFarmTotemPole(ServerLevel level, FarmInstance farm) {
        BlockPos totemPos = farm.getFarmTotemPos();
        level.getChunk(totemPos.getX() >> 4, totemPos.getZ() >> 4);

        Block block = com.stardew.craft.block.ModBlocks.TOTEM_POLE_FARM.get();
        BlockState mainState = block.defaultBlockState()
                .setValue(MapDecorStaticBlock.PART, MapDecorStaticBlock.Part.MAIN)
                .setValue(MapDecorStaticBlock.FACING, Direction.WEST)
                .setValue(com.stardew.craft.block.utility.totem.TotemPoleBlock.ACTIVATED, true);
        level.setBlock(totemPos, mainState, 3);
        block.setPlacedBy(level, totemPos, mainState, null, net.minecraft.world.item.ItemStack.EMPTY);

        if (level.getBlockEntity(totemPos) instanceof com.stardew.craft.blockentity.TotemPoleBlockEntity pole) {
            String poleName = farm.getFarmName();
            com.stardew.craft.totem.TotemPoleTracker tracker = com.stardew.craft.totem.TotemPoleTracker.get(level);
            int poleId = tracker.allocateId();
            tracker.register(poleId, new com.stardew.craft.totem.TotemPoleTracker.PoleEntry(
                    totemPos, poleName, com.stardew.craft.block.utility.totem.TotemType.FARM, false));
            pole.initSystemPole(level, poleId, poleName);
        }
    }

    // ══════════════════════════════════════════
    //  出口交互实体
    // ══════════════════════════════════════════

    private static void spawnExitPortals(
            ServerLevel level,
            FarmInstance farm,
            StardewFarmLayout layout
    ) {
        BlockPos origin = farm.getOrigin();

        spawnExitEntityRegion(level, origin, layout.entrySouth(),
                "sdv_portal_target:farm_exit_south", "sdv_portal_marker:farm_exit");
        spawnExitEntityRegion(level, origin, layout.entryEast(),
                "sdv_portal_target:farm_exit_east", "sdv_portal_marker:farm_exit");
        spawnExitEntityRegion(level, origin, layout.entryWest(),
                "sdv_portal_target:farm_exit_west", "sdv_portal_marker:farm_exit");

        StardewCraft.LOGGER.info("[FARM_INIT] Spawned exit portals for farm of {}", farm.getOwnerName());
    }

    private static void spawnExitEntityRegion(ServerLevel level, BlockPos origin,
                                               StardewFarmLayout.Entry entry,
                                               String targetTag, String markerTag) {
        BlockPos min = origin.offset(entry.exitMin());
        BlockPos max = origin.offset(entry.exitMax());

        int minX = Math.min(min.getX(), max.getX());
        int maxX = Math.max(min.getX(), max.getX());
        int minY = Math.min(min.getY(), max.getY());
        int maxY = Math.max(min.getY(), max.getY());
        int minZ = Math.min(min.getZ(), max.getZ());
        int maxZ = Math.max(min.getZ(), max.getZ());

        // 提取 targetId（去除前缀）
        String targetId = targetTag;
        if (targetTag.startsWith("sdv_portal_target:")) {
            targetId = targetTag.substring("sdv_portal_target:".length());
        }

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    level.setBlock(pos, com.stardew.craft.block.ModBlocks.PORTAL_TRIGGER.get().defaultBlockState(),
                            net.minecraft.world.level.block.Block.UPDATE_ALL);
                    if (level.getBlockEntity(pos) instanceof com.stardew.craft.blockentity.PortalTriggerBlockEntity be) {
                        be.configure(targetId, markerTag);
                    }
                }
            }
        }
    }

    // ══════════════════════════════════════════
    //  农场洞穴系统（外墙 + 传送方块 + 室内）
    // ══════════════════════════════════════════

    /**
     * 老存档兼容：若某农场未放置过洞穴系统（cavePlaced=false），则首次进服时补放。
     * 仅由 {@link com.stardew.craft.player.PlayerDataEventHandler} 在玩家登录并完成离线追赶后调用。
     *
     * @return true 表示本次执行了补放
     */
    public static boolean backfillFarmCaveIfMissing(ServerLevel level, FarmInstance farm) {
        if (farm == null || !farm.isInitialized()) return false;
        StardewFarmLayout layout = farm.getFarmLayout();
        if (layout == null) return false;
        // Repair the door independently of old cavePlaced flags, without clearing farm contents.
        var portal=layout.cavePortalWall();
        if(portal!=null)com.stardew.craft.interior.InteriorSubspaceManager.spawnFarmCaveOutdoorPortalArea(
                level,farm.getOrigin().offset(portal.min()),farm.getOrigin().offset(portal.max()));
        com.stardew.craft.interior.FarmCaveRuntime.request(level,farm);
        return true;
    }

    private static void placeFarmCaveSystem(
            ServerLevel level,
            FarmInstance farm,
            StardewFarmLayout layout
    ) {
        BlockPos origin = farm.getOrigin();

        // 1. 清空区域（仅 FOREST）
        StardewFarmLayout.Region clear = layout.caveClearBox();
        if (clear != null) {
            fillRegion(level, origin, clear, Blocks.AIR.defaultBlockState());
        }

        // 2. 黑色混凝土墙（STANDARD/FOREST）
        StardewFarmLayout.Region blackWall = layout.caveBlackWall();
        if (blackWall != null) {
            fillRegion(level, origin, blackWall, Blocks.BLACK_CONCRETE.defaultBlockState());
        }

        // 3. 外部传送方块
        StardewFarmLayout.Region portalWall = layout.cavePortalWall();
        if (portalWall != null) {
            BlockPos absMin = origin.offset(portalWall.min());
            BlockPos absMax = origin.offset(portalWall.max());
            com.stardew.craft.interior.InteriorSubspaceManager.spawnFarmCaveOutdoorPortalArea(level, absMin, absMax);
        }

        // 4. 室内：为 owner 分配洞穴 origin + 放置 schem + 室内出口传送
        com.stardew.craft.interior.PlayerInteriorAllocator alloc =
                com.stardew.craft.interior.PlayerInteriorAllocator.get(level);
        alloc.ensureCaveLoaded(level, farm.getOwnerUUID());

        StardewCraft.LOGGER.info("[FARM_INIT] Farm cave system placed for {}", farm.getOwnerName());
    }

    /**
     * 在 (origin + region.min)~(origin + region.max) 的立方体区域填充 state。min/max 均包含。
     */
    private static void fillRegion(
            ServerLevel level,
            BlockPos origin,
            StardewFarmLayout.Region region,
            BlockState state
    ) {
        BlockPos min = origin.offset(region.min());
        BlockPos max = origin.offset(region.max());
        int minX = Math.min(min.getX(), max.getX());
        int maxX = Math.max(min.getX(), max.getX());
        int minY = Math.min(min.getY(), max.getY());
        int maxY = Math.max(min.getY(), max.getY());
        int minZ = Math.min(min.getZ(), max.getZ());
        int maxZ = Math.max(min.getZ(), max.getZ());
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    level.setBlock(new BlockPos(x, y, z), state, 3);
                }
            }
        }
    }

    // ══════════════════════════════════════════
    //  辅助
    // ══════════════════════════════════════════

    private static void preloadFarmChunks(ServerLevel level, FarmInstance farm) {        BlockPos min = farm.getFarmBoundsMin();
        BlockPos max = farm.getFarmBoundsMax();
        int minCX = min.getX() >> 4, maxCX = max.getX() >> 4;
        int minCZ = min.getZ() >> 4, maxCZ = max.getZ() >> 4;
        int count = 0;
        for (int cx = minCX; cx <= maxCX; cx++) {
            for (int cz = minCZ; cz <= maxCZ; cz++) {
                level.getChunk(cx, cz);
                count++;
            }
        }
        StardewCraft.LOGGER.info("[FARM_INIT] Pre-loaded {} chunks", count);
    }

    /**
     * 给玩家发放开局物品（如河边农场的熏鱼机）。
     */
    private static void giveStarterItem(ServerLevel level, FarmInstance farm,
                                         net.minecraft.world.item.Item item) {
        var player = level.getServer().getPlayerList().getPlayer(farm.getOwnerUUID());
        if (player != null) {
            net.minecraft.world.item.ItemStack stack = new net.minecraft.world.item.ItemStack(item);
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
            StardewCraft.LOGGER.info("[FARM_INIT] Gave {} to {}", item, farm.getOwnerName());
        }
    }
}
