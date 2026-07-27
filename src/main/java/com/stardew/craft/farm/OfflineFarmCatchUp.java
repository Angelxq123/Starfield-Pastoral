package com.stardew.craft.farm;

import com.stardew.craft.api.v1.agriculture.StardewCropRuntimeAdapter;
import com.stardew.craft.api.v1.internal.crop.StardewCropRuntimeRegistry;
import com.stardew.craft.manager.CropGrowthManager;
import com.stardew.craft.manager.TreeGrowthManager;
import com.stardew.craft.server.performance.PerformanceCounter;
import com.stardew.craft.server.performance.PerformanceTiming;
import com.stardew.craft.server.performance.ServerPerformanceRecorder;
import com.stardew.craft.time.StardewTimeManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

import java.util.UUID;

/** Entry points and single-item operations for offline farm catch-up. */
public final class OfflineFarmCatchUp {
    private OfflineFarmCatchUp() {
    }

    public static int computeAbsoluteDay() {
        StardewTimeManager time = StardewTimeManager.get();
        return (time.getCurrentYear() - 1) * 112
                + time.getCurrentSeason() * 28 + time.getCurrentDay();
    }

    public static int seasonOfAbsDay(int absoluteDay) {
        return ((absoluteDay - 1) / 28) % 4;
    }

    public static void catchUp(ServerLevel level, UUID playerUUID) {
        OfflineFarmCatchUpService.enqueue(level, playerUUID);
    }

    static void growCropOneDay(
            ServerLevel level, CropGrowthManager manager, GlobalPos position) {
        BlockPos blockPos = position.pos();
        if (!level.isLoaded(blockPos)) {
            return;
        }
        StardewCropRuntimeAdapter.DailyResult result =
                StardewCropRuntimeRegistry.growOneDay(level, blockPos, true, true);
        manager.setDirty();
        if (result == StardewCropRuntimeAdapter.DailyResult.REMOVED) {
            manager.removeCrop(level, blockPos);
        }
    }

    static void growTreeOneDay(
            ServerLevel level, TreeGrowthManager manager, GlobalPos position) {
        BlockPos blockPos = position.pos();
        if (!level.isLoaded(blockPos)) {
            return;
        }
        BlockState state = level.getBlockState(blockPos);
        if (state.getBlock()
                instanceof com.stardew.craft.block.tree.WildTreeSaplingBlock) {
            manager.growBy(level, blockPos, 1);
        }
    }

    static void waterSprinkler(ServerLevel level, GlobalPos position) {
        BlockPos blockPos = position.pos();
        if (!level.isLoaded(blockPos)) {
            return;
        }
        BlockState state = level.getBlockState(blockPos);
        if (state.getBlock()
                instanceof com.stardew.craft.block.utility.SprinklerBlock sprinkler) {
            com.stardew.craft.block.utility.SprinklerBlock.waterNow(
                    level, blockPos, sprinkler.getTier());
        }
    }
}
