package com.stardew.craft.npc.runtime;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.core.ModDimensions;
import com.stardew.craft.farm.FarmInstance;
import com.stardew.craft.farm.FarmInstanceRegistry;
import com.stardew.craft.farm.FarmPermissionManager;
import com.stardew.craft.greenhouse.GreenhouseManager;
import com.stardew.craft.network.payload.OpenFarmSelectionPayload;
import com.stardew.craft.network.payload.OpenLewisConfirmPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayDeque;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@EventBusSubscriber(modid = StardewCraft.MODID)
public final class FarmCancellationService {
    private static final Map<UUID, PendingCancellation> PENDING = new ConcurrentHashMap<>();
    private static final Map<UUID, CleanupJob> CLEANUP_BY_OWNER = new ConcurrentHashMap<>();
    private static final ArrayDeque<UUID> CLEANUP_ORDER = new ArrayDeque<>();
    private static final int MAX_BLOCKS_PER_TICK = 1024;
    private static final long MAX_NANOS_PER_TICK = 2_000_000L;

    private FarmCancellationService() {
    }

    public static void requestCancellation(ServerPlayer requester) {
        FarmInstanceRegistry registry = FarmInstanceRegistry.get();
        UUID ownerId = registry.getOwnerForPlayer(requester.getUUID());
        FarmInstance farm = ownerId == null ? null : registry.getFarm(ownerId);
        if (farm == null) {
            PacketDistributor.sendToPlayer(requester, new OpenFarmSelectionPayload());
            return;
        }

        Set<UUID> members = new LinkedHashSet<>(farm.getAllFarmers());
        Set<ServerPlayer> onlineMembers = new LinkedHashSet<>();
        for (UUID memberId : members) {
            ServerPlayer online = requester.server.getPlayerList().getPlayer(memberId);
            if (online == null) {
                requester.displayClientMessage(Component.translatable("stardewcraft.lewis.farm_cancel.offline_blocked"), false);
                return;
            }
            onlineMembers.add(online);
        }

        UUID requestId = UUID.randomUUID();
        PendingCancellation pending = new PendingCancellation(requestId, ownerId, members, new LinkedHashSet<>());
        PENDING.put(requestId, pending);
        for (ServerPlayer member : onlineMembers) {
            PacketDistributor.sendToPlayer(member, new OpenLewisConfirmPayload(
                requestId,
                OpenLewisConfirmPayload.KIND_FARM_CANCEL,
                "stardewcraft.lewis.farm_cancel.question",
                java.util.List.of(farm.getFarmName()),
                "stardewcraft.dialog.yes",
                "stardewcraft.dialog.no"));
        }
    }

    public static void handleConfirm(ServerPlayer responder, UUID requestId, boolean accepted) {
        PendingCancellation pending = PENDING.get(requestId);
        if (pending == null || !pending.required().contains(responder.getUUID())) {
            return;
        }
        if (!accepted) {
            PENDING.remove(requestId);
            notifyMembers(responder.server, pending, Component.translatable(
                    "stardewcraft.lewis.farm_cancel.rejected",
                    com.stardew.craft.player.PlayerDisplayName.get(responder)));
            return;
        }
        PendingCancellation next = pending.withAccepted(responder.getUUID());
        PENDING.put(requestId, next);
        if (next.accepted().containsAll(next.required())) {
            PENDING.remove(requestId);
            executeCancellation(responder, next.ownerId());
        }
    }

    private static void executeCancellation(ServerPlayer actor, UUID ownerId) {
        FarmInstanceRegistry registry = FarmInstanceRegistry.get();
        FarmInstance farm = registry.getFarm(ownerId);
        if (farm == null) {
            return;
        }
        ServerLevel stardewLevel = actor.server.getLevel(ModDimensions.STARDEW_VALLEY);
        if (stardewLevel == null) {
            actor.displayClientMessage(Component.translatable("stardewcraft.farm.not_found"), false);
            return;
        }

        if (CLEANUP_BY_OWNER.containsKey(ownerId)) {
            return;
        }
        Set<UUID> members = new LinkedHashSet<>(farm.getAllFarmers());
        CLEANUP_BY_OWNER.put(
                ownerId,
                new CleanupJob(stardewLevel, farm, members));
        CLEANUP_ORDER.addLast(ownerId);
        StardewCraft.LOGGER.info(
                "[FARM-CANCEL] Scheduled gradual farm cleanup for {}", ownerId);
    }

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || !ModDimensions.STARDEW_VALLEY.equals(level.dimension())) {
            return;
        }
        UUID ownerId = CLEANUP_ORDER.peekFirst();
        if (ownerId == null) {
            return;
        }
        CleanupJob job = CLEANUP_BY_OWNER.get(ownerId);
        if (job == null) {
            CLEANUP_ORDER.removeFirst();
            return;
        }
        if (job.level != level) {
            return;
        }

        FarmInstance registered = FarmInstanceRegistry.get().getFarm(ownerId);
        if (registered != job.farm) {
            removeJob(ownerId, job);
            return;
        }

        try {
            tickCleanup(job);
            if (job.cursor.isComplete()) {
                finishCancellation(ownerId, job);
                removeJob(ownerId, job);
            }
        } catch (RuntimeException exception) {
            StardewCraft.LOGGER.error(
                    "[FARM-CANCEL] Gradual cleanup failed for {} and will retry",
                    ownerId,
                    exception);
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        PENDING.clear();
        for (CleanupJob job : CLEANUP_BY_OWNER.values()) {
            releaseCurrentChunk(job);
        }
        CLEANUP_BY_OWNER.clear();
        CLEANUP_ORDER.clear();
    }

    private static void tickCleanup(CleanupJob job) {
        ChunkPos chunk = job.cursor.currentChunk();
        if (!job.chunkRequested) {
            job.ownedTicket = !job.level.getForcedChunks().contains(chunk.toLong())
                    && job.level.setChunkForced(chunk.x, chunk.z, true);
            job.chunkRequested = true;
            return;
        }
        if (job.level.getChunkSource().getChunkNow(chunk.x, chunk.z) == null) {
            return;
        }
        if (!job.entitiesCleared) {
            clearEntitiesInCurrentChunk(job, chunk);
            job.entitiesCleared = true;
        }

        long deadline = System.nanoTime() + MAX_NANOS_PER_TICK;
        int processed = 0;
        while (processed < MAX_BLOCKS_PER_TICK
                && System.nanoTime() < deadline
                && job.cursor.hasBlockInCurrentChunk()) {
            BlockPos pos = job.cursor.currentBlock();
            if (!job.level.getBlockState(pos).isAir()) {
                job.level.setBlock(pos, Blocks.AIR.defaultBlockState(), 18);
            }
            job.cursor.advanceBlock();
            processed++;
        }

        if (!job.cursor.hasBlockInCurrentChunk()) {
            releaseCurrentChunk(job);
            job.cursor.advanceChunk();
        }
    }

    private static void clearEntitiesInCurrentChunk(CleanupJob job, ChunkPos chunk) {
        BlockPos min = job.farm.getFarmBoundsMin();
        BlockPos max = job.farm.getFarmBoundsMax();
        int minX = Math.max(min.getX(), chunk.getMinBlockX());
        int maxX = Math.min(max.getX(), chunk.getMaxBlockX());
        int minZ = Math.max(min.getZ(), chunk.getMinBlockZ());
        int maxZ = Math.min(max.getZ(), chunk.getMaxBlockZ());
        AABB bounds = new AABB(
                minX, min.getY(), minZ,
                maxX + 1.0D, max.getY() + 1.0D, maxZ + 1.0D);
        for (Entity entity : job.level.getEntities(
                (Entity) null,
                bounds,
                entity -> !(entity instanceof ServerPlayer))) {
            entity.discard();
        }
    }

    private static void finishCancellation(UUID ownerId, CleanupJob job) {
        GreenhouseManager.get(job.level).clearForOwner(ownerId);
        FarmPermissionManager.get().clearAllForOwner(ownerId);
        FarmInstanceRegistry.get().deleteFarm(ownerId);

        for (UUID memberId : job.members) {
            ServerPlayer member = job.level.getServer().getPlayerList().getPlayer(memberId);
            if (member != null) {
                com.stardew.craft.player.PlayerDataEventHandler.syncPlayerData(
                        member,
                        com.stardew.craft.player.PlayerDataManager.getPlayerData(member));
                PacketDistributor.sendToPlayer(member, new OpenFarmSelectionPayload());
                member.displayClientMessage(
                        Component.translatable("stardewcraft.lewis.farm_cancel.completed"),
                        false);
            }
        }
        StardewCraft.LOGGER.info(
                "[FARM-CANCEL] Gradual farm cleanup completed for {}", ownerId);
    }

    private static void removeJob(UUID ownerId, CleanupJob job) {
        releaseCurrentChunk(job);
        CLEANUP_BY_OWNER.remove(ownerId, job);
        CLEANUP_ORDER.remove(ownerId);
    }

    private static void releaseCurrentChunk(CleanupJob job) {
        if (job.ownedTicket && job.chunkRequested && !job.cursor.isComplete()) {
            ChunkPos chunk = job.cursor.currentChunk();
            job.level.setChunkForced(chunk.x, chunk.z, false);
        }
        job.chunkRequested = false;
        job.ownedTicket = false;
        job.entitiesCleared = false;
    }

    private static final class CleanupJob {
        private final ServerLevel level;
        private final FarmInstance farm;
        private final Set<UUID> members;
        private final FarmAreaClearCursor cursor;
        private boolean chunkRequested;
        private boolean ownedTicket;
        private boolean entitiesCleared;

        private CleanupJob(ServerLevel level, FarmInstance farm, Set<UUID> members) {
            this.level = level;
            this.farm = farm;
            this.members = Set.copyOf(members);
            this.cursor = new FarmAreaClearCursor(
                    farm.getFarmBoundsMin(), farm.getFarmBoundsMax());
        }
    }

    private static void notifyMembers(net.minecraft.server.MinecraftServer server, PendingCancellation pending, Component message) {
        for (UUID memberId : pending.required()) {
            ServerPlayer member = server.getPlayerList().getPlayer(memberId);
            if (member != null) {
                member.displayClientMessage(message, false);
            }
        }
    }

    private record PendingCancellation(UUID requestId, UUID ownerId, Set<UUID> required, Set<UUID> accepted) {
        PendingCancellation withAccepted(UUID playerId) {
            Set<UUID> next = new LinkedHashSet<>(accepted);
            next.add(playerId);
            return new PendingCancellation(requestId, ownerId, required, next);
        }
    }
}
