package com.stardew.craft.building.runtime;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

public final class BuildingManagerInteraction {
    private BuildingManagerInteraction() {}
    public static boolean open(ServerPlayer player, BlockPos pos) {
        var data = BuildingWorldData.get(player.serverLevel().getServer());
        var id = data.occupying(player.serverLevel().dimension().location(), pos);
        var record = id == null ? null : data.find(id);
        if (record == null || !record.manager().equals(pos)) return false;
        // Bowl blocks own their watering-can priority and their dedicated management page.
        if (com.stardew.craft.pet.PetBowlBuildings.isBowl(record.family())) return false;
        if (!BuildingService.canManage(player, record)) { BuildingPlacementService.message(player, "permission"); return true; }
        if(!PrefabDefinitions.available(record)){BuildingPlacementService.message(player,"work_stale");return true;}
        BuildingLedgerService.open(player, record);
        return true;
    }
}
