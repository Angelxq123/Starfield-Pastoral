package com.stardew.craft.farm;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.api.v1.internal.farm.StardewFarmLayoutRegistry;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.decor.SupplyCrateBlock;
import com.stardew.craft.time.StardewTimeManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Beach Farm's geometric daily beach-spawn loop, projected onto floating
 * source-water crates beside the authored sand shoreline. */
public final class BeachFarmDailyService {
    private static final Map<UUID, List<BlockPos>> SHORE_CACHE = new HashMap<>();

    private BeachFarmDailyService() {}

    public static void onNewDay(ServerLevel level) {
        if (StardewTimeManager.get().getAbsoluteDay() <= 1) return;
        RandomSource random = level.getRandom();
        CounterData counter = CounterData.get(level);
        int placed = 0;
        for (FarmInstance farm : FarmInstanceRegistry.get(level.getServer()).getAllFarms()) {
            if (!farm.isInitialized() || !farm.getFarmLayoutId().equals(
                    StardewFarmLayoutRegistry.builtinId(FarmType.BEACH))) continue;
            List<BlockPos> shore = SHORE_CACHE.computeIfAbsent(
                    farm.getInstanceId(), ignored -> collectShore(level, farm));
            // Farm.DayUpdate: one eligible beach-spawn attempt for every
            // successful 90% continuation roll (mean nine per day).
            while (random.nextDouble() < 0.90D) {
                if (shore.isEmpty()) break;
                int spawnNumber = counter.next();
                boolean crate = random.nextDouble() < 0.15D || spawnNumber % 4 == 0;
                if (!crate) continue;
                BlockPos pos = shore.get(random.nextInt(shore.size()));
                if (!validShoreWater(level, farm, pos)) continue;
                var state = ModBlocks.SUPPLY_CRATE.get().defaultBlockState()
                        .setValue(SupplyCrateBlock.VARIANT, random.nextInt(3));
                if (state.canSurvive(level, pos) && level.setBlock(pos, state, Block.UPDATE_ALL)) {
                    placed++;
                }
            }
        }
        if (placed > 0) StardewCraft.LOGGER.info(
                "[FARM_DAILY] Floated {} supply crates onto Beach Farm shorelines", placed);
    }

    private static List<BlockPos> collectShore(ServerLevel level, FarmInstance farm) {
        List<BlockPos> result = new ArrayList<>();
        BlockPos origin = farm.getOrigin();
        for (int x = 1; x <= 244; x++) {
            for (int z = 34; z <= 222; z++) {
                // The large north-east lagoon is the farm's freshwater pond,
                // not the supply-crate ocean component.
                if (x >= 115 && x <= 181 && z <= 79) continue;
                BlockPos pos = origin.offset(x, 25, z);
                if (validShoreWater(level, farm, pos)) result.add(pos);
            }
        }
        return List.copyOf(result);
    }

    private static boolean validShoreWater(ServerLevel level, FarmInstance farm, BlockPos pos) {
        if (!farm.contains(pos) || !level.hasChunkAt(pos) || !level.getBlockState(pos).isAir()) return false;
        var support = level.getBlockState(pos.below());
        var fluid = support.getFluidState();
        if (!(support.getBlock() instanceof LiquidBlock)
                || !fluid.is(FluidTags.WATER) || !fluid.isSource()) return false;
        for (int distance = 1; distance <= 2; distance++) {
            for (int dx = -distance; dx <= distance; dx++) {
                for (int dz = -distance; dz <= distance; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != distance) continue;
                    var shore = level.getBlockState(pos.offset(dx, -1, dz));
                    String path = BuiltInRegistries.BLOCK.getKey(shore.getBlock()).getPath();
                    if (shore.is(Blocks.SAND) || path.contains("sand")) return true;
                }
            }
        }
        return false;
    }

    private static final class CounterData extends SavedData {
        private static final String NAME = "stardew_beach_farm_spawns";
        private int count;

        static CounterData get(ServerLevel level) {
            return level.getServer().overworld().getDataStorage().computeIfAbsent(
                    new Factory<>(CounterData::new, CounterData::load), NAME);
        }

        int next() {
            count = Math.addExact(count, 1);
            setDirty();
            return count;
        }

        @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            tag.putInt("Count", count);
            return tag;
        }

        static CounterData load(CompoundTag tag, HolderLookup.Provider registries) {
            CounterData data = new CounterData();
            data.count = Math.max(0, tag.getInt("Count"));
            return data;
        }
    }
}
