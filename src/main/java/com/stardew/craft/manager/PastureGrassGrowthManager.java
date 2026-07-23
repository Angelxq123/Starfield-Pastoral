package com.stardew.craft.manager;

import com.stardew.craft.block.nature.PastureGrassBlock;
import com.stardew.craft.time.StardewTimeManager;
import com.stardew.craft.time.settlement.DailySettlementContext;
import com.stardew.craft.time.settlement.DailySettlementContextFactory;
import com.stardew.craft.time.settlement.DailySettlementRandom;
import com.stardew.craft.time.settlement.DailySettlementWorkUnit;
import com.stardew.craft.time.settlement.DailySettlementWorkUnits;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class PastureGrassGrowthManager extends SavedData {
    private static final String DATA_NAME = "stardew_pasture_grass_growth";
    private boolean processing;

    public static PastureGrassGrowthManager get(ServerLevel level) {
        ServerLevel overworld = level.getServer().overworld();
        return overworld.getDataStorage().computeIfAbsent(
            new SavedData.Factory<>(PastureGrassGrowthManager::new, (tag, provider) -> new PastureGrassGrowthManager()),
            DATA_NAME
        );
    }

    @SuppressWarnings("null")
    public void growDaily(ServerLevel level) {
        DailySettlementWorkUnits.drain(createDailyWorkUnit(
                level,
                DailySettlementContextFactory.captureCurrentDay(StardewTimeManager.get())));
    }

    public DailySettlementWorkUnit createDailyWorkUnit(
            ServerLevel level,
            DailySettlementContext context) {
        if (processing) {
            throw new IllegalStateException("Pasture grass daily work is already active");
        }
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(context, "context");
        processing = true;
        try {
            List<BlockPos> grassSnapshot = collectNearbyPastureGrass(level);
            long worldSeed = level.getSeed();
            int absoluteDay = context.absoluteDay();
            int season = context.season();
            return DailySettlementWorkUnits.cursor(
                    "pasture_grass_growth",
                    grassSnapshot,
                    BlockPos::toShortString,
                    pos -> processPastureGrassDay(level, pos, worldSeed, absoluteDay, season),
                    this::finishDailyProcessing);
        } catch (RuntimeException | Error exception) {
            finishDailyProcessing();
            throw exception;
        }
    }

    @SuppressWarnings("null")
    private void processPastureGrassDay(
            ServerLevel level,
            BlockPos pos,
            long worldSeed,
            int absoluteDay,
            int season) {
        RandomSource random = DailySettlementRandom.forPosition(
                worldSeed, absoluteDay, "pasture_grass", pos);
        if (!level.isLoaded(pos)) {
            return;
        }
        if (!(level.getBlockState(pos).getBlock() instanceof PastureGrassBlock)) {
            return;
        }
        if (season == 3) {
            level.removeBlock(pos, false);
            return;
        }
        if (random.nextDouble() >= 0.65) {
            return;
        }

        for (BlockPos neighbor : List.of(pos.north(), pos.south(), pos.east(), pos.west())) {
            if (!level.isLoaded(neighbor) || random.nextDouble() >= 0.25) {
                continue;
            }
            if (!level.getBlockState(neighbor).isAir()) {
                continue;
            }

            BlockState sourceState = level.getBlockState(pos);
            BlockState grow = sourceState.getBlock().defaultBlockState()
                    .setValue(PastureGrassBlock.VARIANT, random.nextInt(3));
            if (grow.canSurvive(level, neighbor)) {
                level.setBlock(neighbor, grow, 3);
            }
        }
    }

    private void finishDailyProcessing() {
        processing = false;
    }

    @SuppressWarnings("null")
    private List<BlockPos> collectNearbyPastureGrass(ServerLevel level) {
        Set<Long> scannedChunks = new HashSet<>();
        List<BlockPos> results = new ArrayList<>();

        // 只扫描在线玩家农场边界内的区块，而非玩家视距半径
        com.stardew.craft.farm.FarmInstanceRegistry farmReg = com.stardew.craft.farm.FarmInstanceRegistry.get();
        for (net.minecraft.server.level.ServerPlayer player : level.players()) {
            com.stardew.craft.farm.FarmInstance farm = farmReg.getFarmForPlayer(player.getUUID());
            if (farm == null) continue;
            BlockPos min = farm.getFarmBoundsMin();
            BlockPos max = farm.getFarmBoundsMax();
            int minCX = min.getX() >> 4;
            int maxCX = max.getX() >> 4;
            int minCZ = min.getZ() >> 4;
            int maxCZ = max.getZ() >> 4;
            for (int cx = minCX; cx <= maxCX; cx++) {
                for (int cz = minCZ; cz <= maxCZ; cz++) {
                    long key = (((long) cx) << 32) ^ (cz & 0xFFFFFFFFL);
                    if (!scannedChunks.add(key) || !level.hasChunk(cx, cz)) {
                        continue;
                    }

                    int minX = cx << 4;
                    int minZ = cz << 4;
                    for (int x = minX; x < minX + 16; x++) {
                        for (int z = minZ; z < minZ + 16; z++) {
                            int top = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                            int minY = Math.max(level.getMinBuildHeight(), top - 3);
                            int maxY = Math.min(level.getMaxBuildHeight() - 1, top + 1);
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
        }

        return results;
    }

    @Override
    public net.minecraft.nbt.CompoundTag save(@Nonnull net.minecraft.nbt.CompoundTag tag, @Nonnull net.minecraft.core.HolderLookup.Provider provider) {
        return tag;
    }
}
