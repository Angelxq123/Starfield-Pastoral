package com.stardew.craft.farm;

import com.stardew.craft.api.v1.farm.StardewFarmDailyTasks;
import com.stardew.craft.api.v1.internal.farm.StardewFarmDailyTaskRegistry;
import com.stardew.craft.api.v1.internal.farm.StardewFarmSnapshots;
import com.stardew.craft.time.settlement.DailySettlementContext;
import com.stardew.craft.time.settlement.DailySettlementRandom;
import com.stardew.craft.time.settlement.DailySettlementWorkUnit;
import com.stardew.craft.time.settlement.DailySettlementWorkUnits;
import net.minecraft.server.level.ServerLevel;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Budgeted dispatch for addon-provided per-farm daily tasks. */
public final class AddonFarmDailyTaskService {
    private AddonFarmDailyTaskService() {
    }

    public static DailySettlementWorkUnit createDailyWorkUnit(
            ServerLevel level,
            DailySettlementContext context,
            Map<UUID, FarmInstance> frozenFarms
    ) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(frozenFarms, "frozenFarms");
        List<FarmInstance> farms = frozenFarms.values().stream()
                .filter(FarmInstance::isInitialized)
                .sorted(java.util.Comparator.comparing(
                        farm -> farm.getOwnerUUID().toString()))
                .toList();
        long worldSeed = level.getSeed();
        return DailySettlementWorkUnits.cursor(
                "addon_farm_tasks",
                farms,
                farm -> farm.getOwnerUUID().toString(),
                farm -> runFarm(level, context, worldSeed, farm),
                () -> {});
    }

    private static void runFarm(
            ServerLevel level,
            DailySettlementContext context,
            long worldSeed,
            FarmInstance farm
    ) {
        UUID ownerId = farm.getOwnerUUID();
        long stableId = ownerId.getMostSignificantBits()
                ^ Long.rotateLeft(ownerId.getLeastSignificantBits(), 1);
        StardewFarmDailyTaskRegistry.run(new StardewFarmDailyTasks.Context(
                level,
                StardewFarmSnapshots.from(farm),
                context.absoluteDay(),
                context.season(),
                context.day(),
                DailySettlementRandom.forId(
                        worldSeed, context.absoluteDay(), "addon_farm_tasks", stableId)
        ));
    }
}
