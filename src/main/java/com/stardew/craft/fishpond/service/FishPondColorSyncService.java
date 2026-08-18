package com.stardew.craft.fishpond.service;

import com.stardew.craft.fishpond.data.FishPondWorldData;
import com.stardew.craft.fishpond.model.FishPondRecord;
import com.stardew.craft.network.payload.FishPondWaterColorSyncPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;

public final class FishPondColorSyncService {
    private FishPondColorSyncService() {
    }

    public static void sendChunkSnapshot(
            ServerPlayer player,
            ServerLevel level,
            ChunkPos chunkPos
    ) {
        Map<BlockPos, Integer> colors = new LinkedHashMap<>();
        String dimensionId = level.dimension().location().toString();
        for (FishPondRecord pond : FishPondWorldData.get(level).getPonds()) {
            if (!dimensionId.equals(pond.dimensionId()) || pond.waterColor() < 0) {
                continue;
            }
            if (pond.maxX() < chunkPos.getMinBlockX()
                    || pond.minX() > chunkPos.getMaxBlockX()
                    || pond.maxZ() < chunkPos.getMinBlockZ()
                    || pond.minZ() > chunkPos.getMaxBlockZ()) {
                continue;
            }
            for (long packedCell : pond.waterCells()) {
                BlockPos cell = BlockPos.of(packedCell);
                if ((cell.getX() >> 4) == chunkPos.x
                        && (cell.getZ() >> 4) == chunkPos.z) {
                    colors.put(cell, pond.waterColor());
                }
            }
        }
        if (colors.isEmpty()) {
            return;
        }
        PacketDistributor.sendToPlayer(player, FishPondWaterColorSyncPayload.chunkSnapshot(
                dimensionId, chunkPos.x, chunkPos.z, colors));
    }

    public static void clearChunk(ServerPlayer player, ServerLevel level, ChunkPos chunkPos) {
        if (!hasColoredCellInChunk(level, chunkPos)) {
            return;
        }
        PacketDistributor.sendToPlayer(player, FishPondWaterColorSyncPayload.chunkSnapshot(
                level.dimension().location().toString(),
                chunkPos.x,
                chunkPos.z,
                Map.of()));
    }

    private static boolean hasColoredCellInChunk(ServerLevel level, ChunkPos chunkPos) {
        String dimensionId = level.dimension().location().toString();
        for (FishPondRecord pond : FishPondWorldData.get(level).getPonds()) {
            if (!dimensionId.equals(pond.dimensionId()) || pond.waterColor() < 0
                    || pond.maxX() < chunkPos.getMinBlockX()
                    || pond.minX() > chunkPos.getMaxBlockX()
                    || pond.maxZ() < chunkPos.getMinBlockZ()
                    || pond.minZ() > chunkPos.getMaxBlockZ()) {
                continue;
            }
            for (long packedCell : pond.waterCells()) {
                BlockPos cell = BlockPos.of(packedCell);
                if ((cell.getX() >> 4) == chunkPos.x
                        && (cell.getZ() >> 4) == chunkPos.z) {
                    return true;
                }
            }
        }
        return false;
    }

    public static void syncPond(ServerLevel level, FishPondRecord pond) {
        syncPondChange(level, null, pond);
    }

    public static void syncPondChange(
            ServerLevel level,
            @Nullable FishPondRecord previous,
            @Nullable FishPondRecord current
    ) {
        Map<ChunkPos, ChunkDelta> changes = new LinkedHashMap<>();
        if (previous != null) {
            for (long packedCell : previous.waterCells()) {
                BlockPos cell = BlockPos.of(packedCell);
                changes.computeIfAbsent(new ChunkPos(cell), ignored -> new ChunkDelta())
                        .removedCells.add(cell);
            }
        }
        if (current != null) {
            for (long packedCell : current.waterCells()) {
                BlockPos cell = BlockPos.of(packedCell);
                ChunkDelta delta = changes.computeIfAbsent(
                        new ChunkPos(cell), ignored -> new ChunkDelta());
                if (current.waterColor() >= 0) {
                    delta.removedCells.remove(cell);
                    delta.colors.put(cell, current.waterColor());
                } else {
                    delta.colors.remove(cell);
                    delta.removedCells.add(cell);
                }
            }
        }

        String dimensionId = level.dimension().location().toString();
        for (Map.Entry<ChunkPos, ChunkDelta> entry : changes.entrySet()) {
            ChunkPos chunkPos = entry.getKey();
            ChunkDelta delta = entry.getValue();
            if (delta.colors.isEmpty() && delta.removedCells.isEmpty()) {
                continue;
            }
            PacketDistributor.sendToPlayersTrackingChunk(
                    level,
                    chunkPos,
                    FishPondWaterColorSyncPayload.delta(
                            dimensionId,
                            chunkPos.x,
                            chunkPos.z,
                            delta.colors,
                            java.util.List.copyOf(delta.removedCells)));
        }
    }

    private static final class ChunkDelta {
        private final Map<BlockPos, Integer> colors = new LinkedHashMap<>();
        private final LinkedHashSet<BlockPos> removedCells = new LinkedHashSet<>();
    }
}
