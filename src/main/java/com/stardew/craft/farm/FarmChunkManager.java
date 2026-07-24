package com.stardew.craft.farm;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.core.ModDimensions;
import com.stardew.craft.server.performance.DailySettlementMetrics;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;

import javax.annotation.Nullable;
import java.util.*;

/**
 * 农场区块生命周期管理器（轻量版）。
 *
 * 设计原则：**不再永久 forceLoad 农场区块**。
 * - 玩家在场 → MC 原生视距机制自然加载周围区块
 * - 玩家离开 → MC 原生机制自然卸载
 * - 作物系统使用每日结算（CropGrowthManager.growDaily），不依赖 random tick
 * - Utility 机器使用绝对时间戳比较，区块重新加载后自动补偿
 * - 离线追赶（OfflineFarmCatchUp）临时 forceLoad，完成后立即释放
 *
 * 仅保留玩家计数追踪（供其他系统查询）和临时 forceLoad 能力。
 */
public class FarmChunkManager {

    private static final FarmChunkManager INSTANCE = new FarmChunkManager();

    /** 玩家当前所在农场及每个农场的在场人数（供其他系统查询）。 */
    private final FarmOccupancyTracker<UUID> occupancy = new FarmOccupancyTracker<>();

    /** 兼容旧 API 的农场级租约引用，按维度对象 identity 与 slot 隔离。 */
    private final IdentityHashMap<ServerLevel, Map<Integer, TemporaryFarmLoad>> temporaryFarmLoads =
            new IdentityHashMap<>();

    private final TemporaryChunkLeaseTracker<ServerLevel> temporaryChunkLeases =
            new TemporaryChunkLeaseTracker<>(new TemporaryChunkLeaseTracker.Backend<>() {
                @Override
                public boolean acquire(ServerLevel level, ChunkPos chunk) {
                    long chunkKey = chunk.toLong();
                    if (level.getForcedChunks().contains(chunkKey)) {
                        return false;
                    }
                    return level.setChunkForced(chunk.x, chunk.z, true);
                }

                @Override
                public void load(ServerLevel level, ChunkPos chunk) {
                    DailySettlementMetrics.measureSynchronousChunkLoad(
                            level.getServer(), () -> level.getChunk(chunk.x, chunk.z));
                }

                @Override
                public void release(ServerLevel level, ChunkPos chunk) {
                    level.setChunkForced(chunk.x, chunk.z, false);
                }
            });

    static final class DailySettlementChunkLeaseScope<L> implements AutoCloseable {
        private final TemporaryChunkLeaseTracker<L> tracker;
        private final L level;
        private final Set<ChunkPos> heldChunks = new LinkedHashSet<>();
        private final List<TemporaryChunkLeaseTracker.Lease> rootLeases =
                new ArrayList<>();
        private boolean closed;

        DailySettlementChunkLeaseScope(TemporaryChunkLeaseTracker<L> tracker, L level) {
            this.tracker = Objects.requireNonNull(tracker, "tracker");
            this.level = Objects.requireNonNull(level, "level");
        }

        synchronized TemporaryChunkLeaseTracker.Lease lease(Collection<ChunkPos> chunks) {
            if (closed) {
                throw new IllegalStateException("Daily settlement chunk lease scope is closed");
            }
            LinkedHashSet<ChunkPos> newChunks = new LinkedHashSet<>();
            for (ChunkPos chunk : chunks) {
                ChunkPos required = Objects.requireNonNull(chunk, "chunk");
                if (!heldChunks.contains(required)) {
                    newChunks.add(required);
                }
            }
            if (!newChunks.isEmpty()) {
                TemporaryChunkLeaseTracker.Lease rootLease =
                        tracker.acquire(level, newChunks);
                rootLeases.add(rootLease);
                heldChunks.addAll(newChunks);
                if (level instanceof ServerLevel serverLevel) {
                    com.stardew.craft.server.performance.DailySettlementMetrics
                            .recordDailySettlementChunkLeases(
                                    serverLevel.getServer(), newChunks.size());
                }
            }
            return () -> {};
        }

        @Override
        public void close() {
            List<TemporaryChunkLeaseTracker.Lease> leases;
            synchronized (this) {
                if (closed && rootLeases.isEmpty()) {
                    return;
                }
                closed = true;
                leases = new ArrayList<>(rootLeases);
            }

            Throwable failure = null;
            for (TemporaryChunkLeaseTracker.Lease lease : leases) {
                try {
                    lease.close();
                    synchronized (this) {
                        rootLeases.remove(lease);
                    }
                } catch (RuntimeException | Error closeFailure) {
                    if (failure == null) {
                        failure = closeFailure;
                    } else if (failure != closeFailure) {
                        failure.addSuppressed(closeFailure);
                    }
                }
            }
            rethrowUnchecked(failure);
        }

        private static void rethrowUnchecked(@Nullable Throwable failure) {
            if (failure instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (failure instanceof Error error) {
                throw error;
            }
        }

    }

    private static final class TemporaryFarmLoad {
        private final TemporaryChunkLeaseTracker.Lease lease;
        private int references = 1;

        private TemporaryFarmLoad(TemporaryChunkLeaseTracker.Lease lease) {
            this.lease = lease;
        }
    }

    private FarmChunkManager() {}

    public static FarmChunkManager get() {
        return INSTANCE;
    }

    /**
     * 玩家进入农场时调用。
     * 仅追踪玩家计数，不 forceLoad（由 MC 原生视距加载）。
     */
    public void onPlayerEnterFarm(ServerLevel level, ServerPlayer player, FarmInstance farm) {
        int slot = farm.getSlotIndex();
        FarmOccupancyTracker.Transition transition = occupancy.enter(player.getUUID(), slot);

        if (!transition.changed()) {
            return;
        }
        transition.previous().ifPresent(previous ->
            StardewCraft.LOGGER.debug("[FARM_CHUNK] Player {} left farm slot {}, players={}",
                player.getName().getString(), previous.slot(), previous.count()));

        StardewCraft.LOGGER.debug("[FARM_CHUNK] Player {} entered farm slot {}, players={}",
                player.getName().getString(), transition.slot(), transition.count());
    }

    /** Reconcile tracked occupancy from the player's actual dimension and position. */
    public void reconcilePlayerOccupancy(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        if (!ModDimensions.STARDEW_VALLEY.equals(level.dimension())) {
            onPlayerLeaveFarm(level, player);
            return;
        }

        FarmInstance farm = findContainingFarm(
            FarmInstanceRegistry.get().getAllFarms(), player.blockPosition());
        if (farm == null) {
            onPlayerLeaveFarm(level, player);
            return;
        }
        onPlayerEnterFarm(level, player, farm);
    }

    /**
     * 玩家离开农场时调用。
     * 仅减少玩家计数。
     */
    public void onPlayerLeaveFarm(ServerLevel level, ServerPlayer player) {
        occupancy.leave(player.getUUID()).ifPresent(transition -> {
            if (transition.count() == 0) {
                StardewCraft.LOGGER.debug("[FARM_CHUNK] No players in farm slot {}", transition.slot());
            } else {
                StardewCraft.LOGGER.debug("[FARM_CHUNK] Player {} left farm slot {}, players={}",
                        player.getName().getString(), transition.slot(), transition.count());
            }
        });
    }

    /** 兼容旧调用方；离开状态始终以 tracker 中记录的实际 slot 为准。 */
    public void onPlayerLeaveFarm(ServerLevel level, ServerPlayer player, FarmInstance farm) {
        onPlayerLeaveFarm(level, player);
    }

    /**
     * 每 tick 调用（保留接口兼容，当前为空操作）。
     */
    public void tick(ServerLevel level) {
        // 不再需要处理延迟卸载——区块由 MC 原生视距管理
    }

    // ══════════════════════════════════════════
    //  临时 forceLoad（仅用于离线追赶等一次性操作）
    // ══════════════════════════════════════════

    TemporaryChunkLeaseTracker.Lease acquireTemporaryChunks(ServerLevel level, Collection<ChunkPos> chunks) {
        return temporaryChunkLeases.acquire(level, chunks);
    }

    DailySettlementChunkLeaseScope<ServerLevel> beginDailySettlementChunkLeaseScope(ServerLevel level) {
        return new DailySettlementChunkLeaseScope<>(temporaryChunkLeases, level);
    }

    /** 获取一份临时农场区块租约，供离线追赶和每日结算使用。 */
    public void acquireTemporaryFarmChunks(ServerLevel level, int slotIndex) {
        Map<Integer, TemporaryFarmLoad> loadsForLevel = temporaryFarmLoads.get(level);
        TemporaryFarmLoad existing = loadsForLevel == null ? null : loadsForLevel.get(slotIndex);
        if (existing != null) {
            existing.references++;
            return;
        }

        UUID owner = FarmInstanceRegistry.get().getOwnerBySlot(slotIndex);
        if (owner == null) return;
        FarmInstance farm = FarmInstanceRegistry.get().getFarm(owner);
        if (farm == null) return;

        Set<ChunkPos> farmChunks = chunkPositionsForBounds(farm.getFarmBoundsMin(), farm.getFarmBoundsMax());
        TemporaryChunkLeaseTracker.Lease lease = acquireTemporaryChunks(level, farmChunks);
        temporaryFarmLoads.computeIfAbsent(level, ignored -> new HashMap<>())
                .put(slotIndex, new TemporaryFarmLoad(lease));

        StardewCraft.LOGGER.debug("[FARM_CHUNK] Temporarily loaded {} farm chunks for slot {}",
                farmChunks.size(), slotIndex);
    }

    /** 释放一份临时农场区块租约。 */
    public void releaseTemporaryFarmChunks(ServerLevel level, int slotIndex) {
        Map<Integer, TemporaryFarmLoad> loadsForLevel = temporaryFarmLoads.get(level);
        if (loadsForLevel == null) return;

        TemporaryFarmLoad load = loadsForLevel.get(slotIndex);
        if (load == null || --load.references > 0) return;

        loadsForLevel.remove(slotIndex, load);
        if (loadsForLevel.isEmpty()) {
            temporaryFarmLoads.remove(level);
        }

        load.lease.close();
        StardewCraft.LOGGER.debug("[FARM_CHUNK] Released temporary farm chunk lease for slot {}", slotIndex);
    }

    /** 兼容旧调用方。 */
    public void forceLoadFarmChunksForCatchUp(ServerLevel level, int slotIndex) {
        acquireTemporaryFarmChunks(level, slotIndex);
    }

    /** 兼容旧调用方。 */
    public void releaseTempChunks(ServerLevel level, int slotIndex) {
        releaseTemporaryFarmChunks(level, slotIndex);
    }

    static Set<ChunkPos> chunkPositionsForBounds(BlockPos min, BlockPos max) {
        int minCX = Math.min(min.getX(), max.getX()) >> 4;
        int maxCX = Math.max(min.getX(), max.getX()) >> 4;
        int minCZ = Math.min(min.getZ(), max.getZ()) >> 4;
        int maxCZ = Math.max(min.getZ(), max.getZ()) >> 4;
        Set<ChunkPos> chunks = new HashSet<>();
        for (int cx = minCX; cx <= maxCX; cx++) {
            for (int cz = minCZ; cz <= maxCZ; cz++) {
                chunks.add(new ChunkPos(cx, cz));
            }
        }
        return chunks;
    }

    static Set<ChunkPos> chunkPositionsForPosition(BlockPos position, int radius) {
        int safeRadius = Math.max(0, radius);
        return chunkPositionsForBounds(
                position.offset(-safeRadius, 0, -safeRadius),
                position.offset(safeRadius, 0, safeRadius));
    }

    static FarmInstance findContainingFarm(Collection<FarmInstance> farms, BlockPos position) {
        Objects.requireNonNull(farms, "farms");
        Objects.requireNonNull(position, "position");
        for (FarmInstance farm : farms) {
            if (farm.contains(position)) {
                return farm;
            }
        }
        return null;
    }

    // ══════════════════════════════════════════
    //  查询 & 清理
    // ══════════════════════════════════════════

    /**
     * 玩家下线时清理计数。
     */
    public void onPlayerLogout(ServerPlayer player) {
        onPlayerLeaveFarm(player.serverLevel(), player);
    }

    /** 兼容旧调用方；level 不用于推断玩家所属农场。 */
    public void onPlayerLogout(ServerLevel level, ServerPlayer player) {
        onPlayerLogout(player);
    }

    /**
     * 判断某个农场当前是否有玩家在场。
     */
    public boolean isFarmLoaded(int slotIndex) {
        return occupancy.isOccupied(slotIndex);
    }

    /**
     * 获取农场在场玩家数。
     */
    public int getPlayerCount(int slotIndex) {
        return occupancy.count(slotIndex);
    }

    /**
     * 服务器关闭时释放所有临时 forceLoad。
     */
    public void onServerStopping(@Nullable ServerLevel level) {
        try {
            if (level != null) {
                Map<Integer, TemporaryFarmLoad> loadsForLevel = temporaryFarmLoads.remove(level);
                if (loadsForLevel != null) {
                    for (TemporaryFarmLoad load : loadsForLevel.values()) {
                        load.lease.close();
                    }
                }
            }
        } finally {
            try {
                if (level != null) {
                    temporaryChunkLeases.closeAll(level);
                }
            } finally {
                occupancy.clear();
            }
        }
        StardewCraft.LOGGER.info("[FARM_CHUNK] Cleanup on server stop");
    }
}
