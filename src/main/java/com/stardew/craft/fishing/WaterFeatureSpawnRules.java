package com.stardew.craft.fishing;

import com.stardew.craft.core.FarmAreaResolver;
import com.stardew.craft.api.v1.internal.farm.StardewFarmLayoutRegistry;
import com.stardew.craft.farm.FarmInstanceRegistry;
import com.stardew.craft.farm.FarmType;
import com.stardew.craft.fishing.data.FishingDataManager;
import com.stardew.craft.hotspring.HotSpringAreaRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

public final class WaterFeatureSpawnRules {
    private WaterFeatureSpawnRules() {}

    public static boolean canSpawnAt(ServerLevel level, BlockPos pos) {
        return !isBlockedSpawnArea(level, pos);
    }

    public static boolean isBlockedSpawnArea(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null) {
            return true;
        }
        return isInHotSpringArea(level, pos)
            || FarmAreaResolver.isInFarmArea(level, pos)
            || FishingDataManager.hasBiomeTagPublic(level.getBiome(pos), "stardewcraft:is_sewers");
    }

    /**
     * Original Riverland Farm is the only farm type allowed to generate fish
     * splash points. Ore-pan points continue to use the stricter shared rule.
     */
    public static boolean isBlockedFishSplashArea(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null) return true;
        if (isInHotSpringArea(level, pos)
                || FishingDataManager.hasBiomeTagPublic(
                        level.getBiome(pos), "stardewcraft:is_sewers")) {
            return true;
        }
        if (!FarmAreaResolver.isInFarmArea(level, pos)) return false;
        return !isRiverlandFarmArea(level, pos);
    }

    public static boolean isRiverlandFarmArea(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null) return false;
        var farms = FarmInstanceRegistry.get(level.getServer());
        var owner = farms.getOwnerAt(pos);
        var farm = owner == null ? null : farms.getFarm(owner);
        return farm != null && farm.contains(pos)
                && farm.getFarmLayoutId().equals(
                        StardewFarmLayoutRegistry.builtinId(FarmType.RIVERLAND));
    }

    private static boolean isInHotSpringArea(ServerLevel level, BlockPos pos) {
        Vec3 center = Vec3.atCenterOf(pos);
        for (HotSpringAreaRegistry.Area area : HotSpringAreaRegistry.getWaterAreas(level.dimension())) {
            if (area.bounds().contains(center)) {
                return true;
            }
        }
        return false;
    }
}
