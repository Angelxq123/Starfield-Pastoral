package com.stardew.craft.building.runtime;

import com.stardew.craft.core.ModDimensions;
import com.stardew.craft.farm.FarmInstance;
import com.stardew.craft.farm.FarmInstanceRegistry;
import com.stardew.craft.farm.FarmPermissionManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/** Server boundary for building identity/claims. P2 supplies validated definitions and placement checks. */
public final class BuildingService {
    private BuildingService() {}

    public enum Failure { NONE, WRONG_DIMENSION, NO_FARM, OUTSIDE_FARM, FORBIDDEN, OVERLAP }
    public record Registration(Failure failure, UUID buildingId) {}

    /** Geometry must come from a server building definition, not unchecked packet coordinates. */
    public static Registration register(ServerPlayer player, ResourceLocation family, BuildingRecord.Mode mode,
                                        BlockPos anchor, BlockPos manager, Direction facing, BuildingBounds claim) {
        return register(player.serverLevel().getServer(), player.serverLevel().dimension().location(),
                player.getUUID(), family, mode, anchor, manager, facing, claim);
    }

    /** Server command/order entry. Callers resolve dimension and actor; clients cannot select another actor. */
    public static Registration register(MinecraftServer server, ResourceLocation dimension, UUID actor,
                                        ResourceLocation family, BuildingRecord.Mode mode, BlockPos anchor,
                                        BlockPos manager, Direction facing, BuildingBounds claim) {
        if (FishPondPrefabs.isPond(family) && mode == BuildingRecord.Mode.SELF_BUILT) return new Registration(Failure.FORBIDDEN, null);
        if (!server.isSameThread()) throw new IllegalStateException("Building request requires the server thread");
        if (!dimension.equals(ModDimensions.STARDEW_VALLEY.location())) {
            return new Registration(Failure.WRONG_DIMENSION, null);
        }
        FarmInstanceRegistry farms = FarmInstanceRegistry.get(server);
        UUID owner = farms.getOwnerAt(manager);
        FarmInstance farm = owner == null ? null : farms.getFarm(owner);
        if (farm == null) return new Registration(Failure.NO_FARM, null);
        if (!BuildingPlacementService.withinFarm(farm,claim)) {
            return new Registration(Failure.OUTSIDE_FARM, null);
        }
        if (!canManage(actor, farm)) return new Registration(Failure.FORBIDDEN, null);
        BuildingRecord record = BuildingRecord.waiting(farm.getInstanceId(), farm.getSlotIndex(), family,
                mode, dimension, anchor, manager, facing, claim);
        BuildingWorldData.Result result = BuildingWorldData.get(server).register(record);
        if (result == BuildingWorldData.Result.OVERLAP) return new Registration(Failure.OVERLAP, null);
        if (result != BuildingWorldData.Result.SUCCESS) throw new IllegalStateException("Unexpected registration: " + result);
        return new Registration(Failure.NONE, record.id());
    }

    /** Resolve the current owner each time, so ownership transfers do not leave stale permissions. */
    public static boolean canManage(ServerPlayer player, BuildingRecord record) {
        return canManage(player.serverLevel().getServer(), player.getUUID(), record);
    }

    public static boolean canManage(MinecraftServer server, UUID actor, BuildingRecord record) {
        if (!server.isSameThread()) throw new IllegalStateException("Building request requires the server thread");
        FarmInstanceRegistry farms = FarmInstanceRegistry.get(server);
        UUID owner = farms.getOwnerBySlot(record.farmSlot());
        FarmInstance farm = owner == null ? null : farms.getFarm(owner);
        return farm != null && farm.getInstanceId().equals(record.farmId()) && canManage(actor, farm);
    }

    private static boolean canManage(UUID actor, FarmInstance farm) {
        return farm.isFarmer(actor)
                || FarmPermissionManager.get().canModify(farm.getOwnerUUID(), actor);
    }
}
