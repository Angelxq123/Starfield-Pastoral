package com.stardew.craft.animal.runtime;

import com.stardew.craft.building.runtime.*;
import com.stardew.craft.farm.FarmInstance;
import com.stardew.craft.farm.FarmInstanceRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import java.util.UUID;

/** Farm UUID is the account; player UUIDs and reusable farm slots are never feed accounts. */
public final class FarmFeed {
    private FarmFeed() {}
    public static FarmInstance farm(ServerLevel level, BlockPos pos) {
        if (!level.dimension().equals(com.stardew.craft.core.ModDimensions.STARDEW_VALLEY)) return null;
        var farms = FarmInstanceRegistry.get(level.getServer()); var owner = farms.getOwnerAt(pos);
        return owner == null ? null : farms.getFarm(owner);
    }
    public static BuildingRecord home(ServerLevel level, BlockPos pos) {
        var buildings = BuildingWorldData.get(level.getServer()); var id = buildings.occupying(level.dimension().location(), pos);
        return id == null ? null : buildings.find(id);
    }
    public static int capacity(MinecraftServer server, UUID farm) {
        var buildings=BuildingWorldData.get(server);
        // Capacity is queried by hay collection and feeding, so it is also the last reliable
        // recovery point for a completed prefab whose post-construction assessment was
        // interrupted. Self-built and purchased silos must follow the same validity check.
        for(var silo:buildings.all()) if(silo.farmId().equals(farm) && silo.family().equals(UtilityBuildings.SILO)) {
            var level=server.getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,silo.dimension()));
            if(level!=null)UtilityBuildings.refresh(level,silo);
        }
        return buildings.all().stream().filter(b -> b.farmId().equals(farm)
                && b.family().equals(UtilityBuildings.SILO) && b.phase() == BuildingRecord.Phase.READY && b.residence() == BuildingRecord.Residence.VALID).mapToInt(b -> {
                    var rules=LivestockHomes.rules(b);
                    return rules==null?UtilityBuildings.SILO_CAPACITY:rules.hayCapacity();
                }).sum();
    }
    public static int amount(MinecraftServer server, UUID farm) { return LivestockWorldData.get(server).hay(farm); }
    public static int store(ServerLevel level, BlockPos pos, int requested) {
        var farm = farm(level, pos); if (farm == null) return 0;
        return store(level.getServer(), farm.getInstanceId(), requested);
    }
    public static int store(MinecraftServer server, UUID farm, int requested) {
        LivestockService.recover(server); var data = LivestockWorldData.get(server); int stored = Math.max(0, Math.min(requested, capacity(server, farm) - data.hay(farm)));
        if (stored > 0) data.hay(farm, data.hay(farm) + stored); return stored;
    }
    public static int take(MinecraftServer server, UUID farm, int requested) {
        LivestockService.recover(server); var data = LivestockWorldData.get(server); int taken = Math.max(0, Math.min(requested, data.hay(farm)));
        if (taken > 0) data.hay(farm, data.hay(farm) - taken); return taken;
    }
}
