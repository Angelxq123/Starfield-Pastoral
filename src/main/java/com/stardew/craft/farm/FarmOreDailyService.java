package com.stardew.craft.farm;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.api.v1.internal.farm.StardewFarmLayoutRegistry;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.manager.ArtifactSpotSpawnService;
import com.stardew.craft.mining.MineStoneMining;
import com.stardew.craft.player.PlayerStardewDataAPI;
import com.stardew.craft.player.SkillType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;

/** Original Hilltop/Four Corners daily ore rolls over their authored loose-soil masks. */
public final class FarmOreDailyService {
    public record OreRoll(String sourceId, int health, boolean artifactSpot) {}
    private record Zone(BlockPos min, BlockPos max, int newFarmRounds) {}

    private static final Zone HILLTOP = new Zone(
            new BlockPos(75, 24, 154), new BlockPos(110, 24, 180), 28);
    private static final Zone FOUR_CORNERS = new Zone(
            new BlockPos(157, 24, 189), new BlockPos(171, 24, 197), 10);

    private FarmOreDailyService() {}

    public static void seedNewFarm(ServerLevel level, FarmInstance farm) {
        Zone zone = zone(farm);
        if (zone != null) runRounds(level, farm, zone, zone.newFarmRounds());
    }

    public static void onNewDay(ServerLevel level) {
        for (FarmInstance farm : FarmInstanceRegistry.get(level.getServer()).getAllFarms()) {
            Zone zone = zone(farm);
            if (farm.isInitialized() && zone != null) runRounds(level, farm, zone, 1);
        }
    }

    private static Zone zone(FarmInstance farm) {
        if (farm.getFarmLayoutId().equals(StardewFarmLayoutRegistry.builtinId(FarmType.HILLTOP))) {
            return HILLTOP;
        }
        if (farm.getFarmLayoutId().equals(StardewFarmLayoutRegistry.builtinId(FarmType.FOUR_CORNERS))) {
            return FOUR_CORNERS;
        }
        return null;
    }

    private static void runRounds(ServerLevel level, FarmInstance farm, Zone zone, int rounds) {
        int miningLevel = level.getServer().getPlayerList().getPlayers().stream()
                .filter(player -> farm.isFarmer(player.getUUID()))
                .mapToInt(player -> PlayerStardewDataAPI.getSkillLevel(player, SkillType.MINING))
                .max().orElse(0);
        int placed = 0;
        RandomSource random = level.getRandom();
        for (int round = 0; round < rounds; round++) {
            double chance = 1.0D;
            while (random.nextDouble() < chance) {
                int x = zone.min().getX() + random.nextInt(zone.max().getX() - zone.min().getX() + 1);
                int z = zone.min().getZ() + random.nextInt(zone.max().getZ() - zone.min().getZ() + 1);
                BlockPos ground = farm.getOrigin().offset(x, zone.min().getY(), z);
                BlockPos target = ground.above();
                if (level.isLoaded(target)
                        && level.getBlockState(ground).is(ModBlocks.MINE_EARTH_LOOSE_SOIL.get())
                        && level.getBlockState(target).isAir()) {
                    OreRoll roll = rollOre(random, miningLevel);
                    if (roll.artifactSpot()) {
                        if (level.setBlock(target, ModBlocks.ARTIFACT_SPOT.get().defaultBlockState(), Block.UPDATE_ALL)) {
                            ArtifactSpotSpawnService.track(level, target);
                            placed++;
                        }
                    } else {
                        var state = MineStoneMining.stateForSource(roll.sourceId(), roll.health());
                        if (state.isPresent() && level.setBlock(target, state.get(), Block.UPDATE_ALL)) placed++;
                    }
                }
                chance *= 0.75D;
            }
        }
        if (placed > 0) StardewCraft.LOGGER.info(
                "[FARM_ORE] Spawned {} Hilltop/Four Corners nodes for {}", placed, farm.getOwnerName());
    }

    /** Exact source branch order from Farm.doDailyMountainFarmUpdate. */
    public static OreRoll rollOre(RandomSource random, int miningLevel) {
        if (random.nextDouble() < 0.15D) return new OreRoll("590", 0, true);
        String source = random.nextBoolean() ? "670" : "668";
        int health = 2;
        if (random.nextDouble() < 0.10D) {
            if (miningLevel >= 8 && random.nextDouble() < 0.33D) {
                source = "77"; health = 7;
            } else if (miningLevel >= 5 && random.nextBoolean()) {
                source = "76"; health = 5;
            } else {
                source = "75"; health = 3;
            }
        }
        if (random.nextDouble() < 0.21D) { source = "751"; health = 3; }
        if (miningLevel >= 4 && random.nextDouble() < 0.15D) { source = "290"; health = 4; }
        if (miningLevel >= 7 && random.nextDouble() < 0.10D) { source = "764"; health = 8; }
        if (miningLevel >= 10 && random.nextDouble() < 0.01D) { source = "765"; health = 16; }
        return new OreRoll(source, health, false);
    }
}
