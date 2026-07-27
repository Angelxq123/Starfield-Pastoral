package com.stardew.craft.farm;

import com.stardew.craft.api.v1.agriculture.StardewCropRemovalCause;
import com.stardew.craft.api.v1.agriculture.StardewCropRuntime;
import com.stardew.craft.api.v1.farm.StardewFarmDebrisPlacements;
import com.stardew.craft.api.v1.farm.StardewFarmSnapshot;
import com.stardew.craft.api.v1.internal.farm.StardewFarmDebrisPlacementRegistry;
import com.stardew.craft.api.v1.internal.farm.StardewFarmSnapshots;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.crop.StardewCropBlock;
import com.stardew.craft.block.nature.PastureGrassBlock;
import com.stardew.craft.block.nature.WildWeedsBlock;
import com.stardew.craft.time.StardewTimeManager;
import com.stardew.craft.time.settlement.DailySettlementContext;
import com.stardew.craft.time.settlement.DailySettlementContextFactory;
import com.stardew.craft.time.settlement.DailySettlementRandom;
import com.stardew.craft.time.settlement.DailySettlementWorkUnit;
import com.stardew.craft.time.settlement.DailySettlementWorkUnits;
import com.stardew.craft.weather.WeatherManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Original-style daily weed, stone and existing fallen-log spreading for player farms. */
@SuppressWarnings("null")
public final class FarmDebrisDailyService {
    private FarmDebrisDailyService() {
    }

    public static void onNewDay(ServerLevel level) {
        DailySettlementContext context = DailySettlementContextFactory.captureCurrentDay(
                StardewTimeManager.get());
        DailySettlementWorkUnits.drain(createDailyWorkUnit(
                level, context, snapshotOnlineFarms(level)));
    }

    public static DailySettlementWorkUnit createDailyWorkUnit(
            ServerLevel level,
            DailySettlementContext context,
            Map<UUID, FarmInstance> frozenFarms) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(frozenFarms, "frozenFarms");
        if (context.season() == 3) {
            return new DebrisWorkUnit(level, context, List.of());
        }

        int spreadAttempts = adjustedAttemptCount(
                WeatherManager.isRaining(level), context.day(), context.season() == 1 ? 30 : 20);
        int randomAttempts = context.day() == 1
                ? adjustedAttemptCount(WeatherManager.isRaining(level), context.day(), 20) : 0;
        int springAttempts = context.day() == 1 && context.season() == 0
                && context.absoluteDay() > 1
                ? adjustedAttemptCount(WeatherManager.isRaining(level), context.day(), 40) : 0;

        List<FarmState> farms = frozenFarms.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(java.util.Comparator.comparing(UUID::toString)))
                .map(entry -> new FarmState(
                        entry.getKey(), entry.getValue(), spreadAttempts, randomAttempts, springAttempts))
                .filter(state -> state.farm.isInitialized() && !state.farm.hasActiveGoldClock())
                .toList();
        return new DebrisWorkUnit(level, context, farms);
    }

    private static Map<UUID, FarmInstance> snapshotOnlineFarms(ServerLevel level) {
        Set<UUID> processed = new HashSet<>();
        FarmInstanceRegistry registry = FarmInstanceRegistry.get();
        Map<UUID, FarmInstance> farms = new LinkedHashMap<>();
        for (var player : level.players()) {
            FarmInstance farm = registry.getFarmForPlayer(player.getUUID());
            if (farm != null && processed.add(farm.getOwnerUUID())) {
                farms.put(farm.getOwnerUUID(), farm);
            }
        }
        return Map.copyOf(farms);
    }

    /** GameLocation.spawnWeedsAndStones applies both multipliers to every call. */
    private static int adjustedAttemptCount(boolean raining, int day, int baseCount) {
        int attempts = raining ? baseCount * 2 : baseCount;
        return day == 1 ? attempts * 5 : attempts;
    }

    private static void spawnRandomDebrisAttempt(
            ServerLevel level, FarmInstance farm, boolean weedsOnly, int season, RandomSource random) {
        BlockPos target = findRandomEmptyPlace(level, farm, random);
        if (target == null) {
            return;
        }
        StardewFarmSnapshot farmSnapshot = StardewFarmSnapshots.from(farm);
        boolean debrisRoll = random.nextBoolean();
        BlockState placed = debrisRoll && !weedsOnly
                ? randomDebrisState(random)
                : ModBlocks.WILD_WEEDS.get().defaultBlockState()
                        .setValue(WildWeedsBlock.SEASON, season)
                        .setValue(WildWeedsBlock.VARIANT, random.nextInt(3));
        if (random.nextDouble() < 0.05D) {
            com.stardew.craft.tree.WildTrees.Def[] trees = {
                    com.stardew.craft.tree.WildTrees.OAK,
                    com.stardew.craft.tree.WildTrees.MAPLE,
                    com.stardew.craft.tree.WildTrees.PINE
            };
            com.stardew.craft.tree.WildTrees.Def tree = trees[random.nextInt(trees.length)];
            BlockState sapling = (random.nextInt(3) == 0
                    ? tree.sapling0().get() : tree.sapling1().get()).defaultBlockState();
            sapling = StardewFarmDebrisPlacementRegistry.resolve(
                    new StardewFarmDebrisPlacements.Context(
                            level,
                            farmSnapshot,
                            target,
                            sapling,
                            StardewFarmDebrisPlacements.Stage.YOUNG_TREE,
                            random));
            if (sapling.canSurvive(level, target)) {
                level.setBlock(target, sapling, 3);
                return;
            }
        }
        placed = StardewFarmDebrisPlacementRegistry.resolve(
                new StardewFarmDebrisPlacements.Context(
                        level,
                        farmSnapshot,
                        target,
                        placed,
                        StardewFarmDebrisPlacements.Stage.DEBRIS,
                        random));
        level.setBlock(target, placed, 3);
    }

    private static void spreadFromExistingDebrisAttempt(
            ServerLevel level,
            FarmInstance farm,
            List<BlockPos> farmObjects,
            int season,
            RandomSource random) {
        if (farmObjects.isEmpty()) {
            return;
        }
        int dx;
        int dz;
        do {
            dx = random.nextInt(3) - 1;
            dz = random.nextInt(3) - 1;
        } while (dx == 0 && dz == 0);
        BlockPos source = farmObjects.get(random.nextInt(farmObjects.size()));
        BlockState sourceState = level.getBlockState(source);
        Block sourceBlock = sourceState.getBlock();
        if (!isSpreadSource(sourceState)) {
            return;
        }
        BlockPos target = findDebrisPlaceNear(level, farm, source.offset(dx, 0, dz));
        if (target == null || level.getBlockEntity(target) != null
                || !canDebrisReplace(level, target, level.getBlockState(target))) {
            return;
        }
        BlockState placed;
        if (sourceBlock instanceof WildWeedsBlock) {
            random.nextBoolean();
            placed = ModBlocks.WILD_WEEDS.get().defaultBlockState()
                    .setValue(WildWeedsBlock.SEASON, season)
                    .setValue(WildWeedsBlock.VARIANT, random.nextInt(3));
        } else {
            if (!random.nextBoolean()) {
                return;
            }
            placed = switch (random.nextInt(4)) {
                case 0, 1 -> fallenLogState(random);
                case 2 -> ModBlocks.EARTH_SHALE.get().defaultBlockState();
                default -> ModBlocks.MOSSY_SANDSTONE.get().defaultBlockState();
            };
        }
        if (StardewCropRuntime.inspect(level, target) != null
                && !StardewCropRuntime.remove(
                        level, target, StardewCropRemovalCause.FARM_DEBRIS)) {
            return;
        }
        clearTilledGroundBelow(level, target);
        level.setBlock(target, placed, 3);
        if (!farmObjects.contains(target)) {
            farmObjects.add(target.immutable());
        }
    }

    private static void collectFarmObjectAt(
            ServerLevel level, FarmInstance farm, List<BlockPos> objects, int x, int z) {
        BlockPos top = findTopBlock(level, x, z,
                farm.getFarmBoundsMin().getY(), farm.getFarmBoundsMax().getY());
        if (top == null) {
            return;
        }
        BlockState state = level.getBlockState(top);
        Block block = state.getBlock();
        if (isSpreadSource(state) || block instanceof FenceBlock || level.getBlockEntity(top) != null) {
            objects.add(top.immutable());
        }
    }

    private static final class FarmState {
        private final UUID ownerId;
        private final FarmInstance farm;
        private final FarmDebrisCursor cursor;
        private final List<BlockPos> objects = new ArrayList<>();

        private FarmState(
                UUID ownerId,
                FarmInstance farm,
                int spreadAttempts,
                int randomAttempts,
                int springAttempts) {
            this.ownerId = ownerId;
            this.farm = farm;
            BlockPos min = farm.getFarmBoundsMin();
            BlockPos max = farm.getFarmBoundsMax();
            cursor = new FarmDebrisCursor(
                    min.getX(), max.getX(), min.getZ(), max.getZ(),
                    spreadAttempts, randomAttempts, springAttempts);
        }
    }

    private static final class DebrisWorkUnit implements DailySettlementWorkUnit {
        private final ServerLevel level;
        private final DailySettlementContext context;
        private final List<FarmState> farms;
        private int farmIndex;

        private DebrisWorkUnit(
                ServerLevel level, DailySettlementContext context, List<FarmState> farms) {
            this.level = level;
            this.context = context;
            this.farms = List.copyOf(farms);
            advanceCompletedFarms();
        }

        @Override
        public String name() {
            return "farm_debris";
        }

        @Override
        public String currentItemIdentity() {
            FarmState state = currentFarm();
            return state.ownerId + ":" + state.cursor.current().identity();
        }

        @Override
        public boolean isComplete() {
            advanceCompletedFarms();
            return farmIndex >= farms.size();
        }

        @Override
        public void runNext() {
            FarmState state = currentFarm();
            FarmDebrisCursor.Step step = state.cursor.current();
            if (step.phase() == FarmDebrisCursor.Phase.SPREAD && state.objects.isEmpty()) {
                state.cursor.skipSpread();
                advanceCompletedFarms();
                return;
            }

            if (step.phase() == FarmDebrisCursor.Phase.SCAN) {
                collectFarmObjectAt(level, state.farm, state.objects, step.x(), step.z());
            } else {
                RandomSource random = DailySettlementRandom.forId(
                        level.getSeed(), context.absoluteDay(), "farm_debris",
                        stableStepId(state.ownerId, step));
                switch (step.phase()) {
                    case SPREAD -> spreadFromExistingDebrisAttempt(
                            level, state.farm, state.objects, context.season(), random);
                    case RANDOM, SPRING_RANDOM -> spawnRandomDebrisAttempt(
                            level, state.farm, false, context.season(), random);
                    case SPRING_WEEDS -> spawnRandomDebrisAttempt(
                            level, state.farm, true, context.season(), random);
                    default -> throw new IllegalStateException("Unexpected debris phase " + step.phase());
                }
            }
            state.cursor.advance();
            advanceCompletedFarms();
        }

        @Override
        public void skipFailedItem() {
            FarmState state = currentFarm();
            state.cursor.advance();
            advanceCompletedFarms();
        }

        private FarmState currentFarm() {
            advanceCompletedFarms();
            if (farmIndex >= farms.size()) {
                throw new IllegalStateException("Farm debris work is complete");
            }
            return farms.get(farmIndex);
        }

        private void advanceCompletedFarms() {
            while (farmIndex < farms.size() && farms.get(farmIndex).cursor.isComplete()) {
                farmIndex++;
            }
        }

        private static long stableStepId(UUID ownerId, FarmDebrisCursor.Step step) {
            long phase = (long) step.phase().ordinal() << 56;
            return ownerId.getMostSignificantBits() ^ ownerId.getLeastSignificantBits()
                    ^ phase ^ Integer.toUnsignedLong(step.attempt());
        }
    }

    @Nullable
    private static BlockPos findRandomEmptyPlace(ServerLevel level, FarmInstance farm, RandomSource random) {
        BlockPos min = farm.getFarmBoundsMin();
        BlockPos max = farm.getFarmBoundsMax();
        int x = min.getX() + random.nextInt(max.getX() - min.getX() + 1);
        int z = min.getZ() + random.nextInt(max.getZ() - min.getZ() + 1);
        BlockPos top = findTopBlock(level, x, z, min.getY(), max.getY());
        if (top == null || !isDiggableFarmGround(level.getBlockState(top).getBlock())
                || level.getBlockState(top).is(Blocks.FARMLAND)) {
            return null;
        }
        BlockPos place = top.above();
        return level.getBlockState(place).isAir() ? place : null;
    }

    @Nullable
    private static BlockPos findDebrisPlaceNear(ServerLevel level, FarmInstance farm, BlockPos near) {
        if (!farm.contains(near)) {
            return null;
        }
        for (int y = near.getY() + 1; y >= near.getY() - 1; y--) {
            BlockPos place = new BlockPos(near.getX(), y, near.getZ());
            BlockState ground = level.getBlockState(place.below());
            if (isDiggableFarmGround(ground.getBlock())
                    && canDebrisReplace(level, place, level.getBlockState(place))) {
                return place;
            }
        }
        return null;
    }

    @Nullable
    private static BlockPos findTopBlock(ServerLevel level, int x, int z, int minY, int maxY) {
        for (int y = maxY; y >= minY; y--) {
            BlockPos pos = new BlockPos(x, y, z);
            if (!level.isLoaded(pos)) {
                return null;
            }
            if (!level.getBlockState(pos).isAir()) {
                return pos;
            }
        }
        return null;
    }

    private static boolean isSpreadSource(BlockState state) {
        Block block = state.getBlock();
        return block instanceof WildWeedsBlock
                || isFarmStone(block)
                || isFarmLog(state);
    }

    private static boolean isDiggableFarmGround(Block block) {
        return block == ModBlocks.YELLOW_DIRT.get()
                || block == Blocks.GRASS_BLOCK
                || block == Blocks.FARMLAND;
    }

    private static boolean canDebrisReplace(
            ServerLevel level, BlockPos position, BlockState state) {
        Block block = state.getBlock();
        return state.isAir()
                || block instanceof WildWeedsBlock
                || block instanceof PastureGrassBlock
                || block instanceof StardewCropBlock
                || StardewCropRuntime.inspect(level, position) != null
                || isFarmStone(block)
                || isFarmLog(state);
    }

    /** Spreading debris destroys HoeDirt in SDV, exposing the farm's dirt tile. */
    private static void clearTilledGroundBelow(ServerLevel level, BlockPos place) {
        BlockPos ground = place.below();
        if (level.getBlockState(ground).is(Blocks.FARMLAND)) {
            level.setBlock(ground, ModBlocks.YELLOW_DIRT.get().defaultBlockState(), 3);
        }
    }

    /** SDV 294/295 are mapped to the project's existing horizontal log, not a new twig block. */
    private static BlockState fallenLogState(RandomSource random) {
        return ModBlocks.OAK_LOG.get().defaultBlockState().setValue(
                RotatedPillarBlock.AXIS, random.nextBoolean() ? Direction.Axis.X : Direction.Axis.Z);
    }

    private static BlockState randomDebrisState(RandomSource random) {
        return switch (random.nextInt(4)) {
            case 0, 1 -> fallenLogState(random);
            case 2 -> ModBlocks.EARTH_SHALE.get().defaultBlockState();
            default -> ModBlocks.MOSSY_SANDSTONE.get().defaultBlockState();
        };
    }

    private static boolean isFarmStone(Block block) {
        return block == ModBlocks.EARTH_SHALE.get() || block == ModBlocks.MOSSY_SANDSTONE.get();
    }

    private static boolean isFarmLog(BlockState state) {
        Block block = state.getBlock();
        return (block == ModBlocks.OAK_LOG.get()
                || block == ModBlocks.MAPLE_LOG.get()
                || block == ModBlocks.PINE_LOG.get())
                && state.hasProperty(RotatedPillarBlock.AXIS)
                && state.getValue(RotatedPillarBlock.AXIS) != Direction.Axis.Y;
    }
}
