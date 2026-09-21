package com.stardew.craft.building.runtime;

import com.stardew.craft.greenhouse.GreenhouseBuildings;
import com.stardew.craft.interior.InteriorSubspaceManager;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

/** Hides buildings during move preview and restores abandoned durable snapshots. */
public final class BuildingMoveSession {
    private BuildingMoveSession() {}

    public static boolean begin(ServerPlayer player, ItemStack document, BuildingRecord record) {
        UUID draft = BuildingDrafts.id(document);
        if (draft == null || record == null || record.phase() != BuildingRecord.Phase.READY
                || !record.dimension().equals(player.serverLevel().dimension().location())
                || !BuildingService.canManage(player, record)) return false;
        ServerLevel level = player.serverLevel();
        if (!level.hasChunksAt(record.claim().min(), record.claim().maxInclusive())) return false;
        BuildingTransfer snapshot;
        try {
            snapshot = BuildingTransfer.move(level, record, record.anchor(), record.facing());
        } catch (BuildingTransfer.Collision collision) {
            return false;
        }
        BuildingMoveLift lift = new BuildingMoveLift(player.getUUID(), draft, snapshot,
                BuildingMovePreview.capture(player, record));
        BuildingWorldData data = BuildingWorldData.get(player.server);
        if (data.beginMoveLift(lift) != BuildingWorldData.Result.SUCCESS) return false;
        player.server.overworld().getDataStorage().save();
        try {
            hideWorld(level, lift);
            return true;
        } catch (RuntimeException exception) {
            restoreWorld(level, lift);
            data.finishMoveLift(record.id(), player.getUUID(), draft);
            player.server.overworld().getDataStorage().save();
            throw exception;
        }
    }

    public static BuildingMoveLift lift(MinecraftServer server, UUID building) {
        return BuildingWorldData.get(server).moveLift(building);
    }

    public static boolean restoreHeld(ServerPlayer player, ItemStack document) {
        var tag = BuildingBlueprintItem.draft(document);
        UUID draft = BuildingDrafts.id(document);
        if (draft == null || !tag.hasUUID("MoveBuilding")) return false;
        BuildingWorldData data = BuildingWorldData.peek(player.server);
        if (data == null || data.moveLift(tag.getUUID("MoveBuilding")) == null) return false;
        return restore(player.server, tag.getUUID("MoveBuilding"), player.getUUID(), draft);
    }

    public static void restoreOwner(ServerPlayer player) {
        for (BuildingMoveLift lift : BuildingWorldData.get(player.server).moveLifts()) {
            if (lift.owner().equals(player.getUUID())) {
                UUID building=lift.snapshot().before().id();
                if(restore(player.server,building,lift.owner(),lift.document())) {
                    BuildingBlueprintItem.consumeMoveDocuments(player,building);
                }
            }
        }
    }

    public static void recoverAbandoned(MinecraftServer server) {
        BuildingWorldData data = BuildingWorldData.get(server);
        for (BuildingMoveLift lift : data.moveLifts()) {
            ServerPlayer player = server.getPlayerList().getPlayer(lift.owner());
            if (player != null && ownsDocument(player, lift)) continue;
            restore(server, lift.snapshot().before().id(), lift.owner(), lift.document());
        }
    }

    public static void restoreForAttempt(ServerLevel level, BuildingMoveLift lift) {
        load(level, lift.snapshot().before().claim());
        restoreWorld(level, lift);
    }

    public static void hideAfterFailedAttempt(ServerLevel level, BuildingMoveLift lift) {
        hideWorld(level, lift);
    }

    private static boolean restore(
            MinecraftServer server, UUID building, UUID owner, UUID document) {
        BuildingWorldData data = BuildingWorldData.get(server);
        BuildingMoveLift lift = data.moveLift(building);
        if (lift == null || !lift.owner().equals(owner) || !lift.document().equals(document)) return false;
        ServerLevel level = level(server, lift.snapshot().before());
        if (level == null) return false;
        load(level, lift.snapshot().before().claim());
        restoreWorld(level, lift);
        if (data.finishMoveLift(building, owner, document) == null) return false;
        server.overworld().getDataStorage().save();
        return true;
    }

    private static void hideWorld(ServerLevel level, BuildingMoveLift lift) {
        BuildingRecord record = lift.snapshot().before();
        if (GreenhouseBuildings.isGreenhouse(record.family())) {
            InteriorSubspaceManager.removeGreenhouseOutdoorPortalAt(
                    level, GreenhouseBuildings.portal(record));
        }
        lift.snapshot().lift(level);
    }

    private static void restoreWorld(ServerLevel level, BuildingMoveLift lift) {
        lift.snapshot().restoreLift(level);
        BuildingRecord record = lift.snapshot().before();
        if (GreenhouseBuildings.isGreenhouse(record.family())) {
            GreenhouseBuildings.ensurePortal(level, record);
        }
    }

    private static boolean ownsDocument(ServerPlayer player, BuildingMoveLift lift) {
        var stacks = new java.util.ArrayList<ItemStack>(player.getInventory().items);
        stacks.add(player.getOffhandItem());
        for (ItemStack stack : stacks) {
            var tag = BuildingBlueprintItem.draft(stack);
            if (stack.getItem() instanceof BuildingBlueprintItem
                    && lift.document().equals(BuildingDrafts.id(stack))
                    && tag.hasUUID("MoveBuilding")
                    && lift.snapshot().before().id().equals(tag.getUUID("MoveBuilding"))) return true;
        }
        return false;
    }

    private static ServerLevel level(MinecraftServer server, BuildingRecord record) {
        return server.getLevel(ResourceKey.create(Registries.DIMENSION, record.dimension()));
    }

    private static void load(ServerLevel level, BuildingBounds bounds) {
        for (int x = bounds.min().getX() >> 4; x <= bounds.maxInclusive().getX() >> 4; x++) {
            for (int z = bounds.min().getZ() >> 4; z <= bounds.maxInclusive().getZ() >> 4; z++) {
                level.getChunk(x, z);
            }
        }
    }
}
