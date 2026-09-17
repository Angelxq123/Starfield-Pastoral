package com.stardew.craft.interior;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.block.ModBlocks;
import net.minecraft.core.BlockPos;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import java.util.Map;
import java.util.Set;

/**
 * 主世界巫师塔结构后处理：
 * 1. 玩家首次接近时，一次性将结构周围暴露泥土转为草方块
 * 2. 在入口放置交互实体（传送门触发器）
 *
 * 玩家事件只去重排队；服务端每 tick 按时间和方块数预算扫描已加载区块。
 */
@EventBusSubscriber(modid = StardewCraft.MODID)
@SuppressWarnings("null")
public final class WizardTowerStructureHandler {

    private static final ResourceKey<Structure> WIZARD_TOWER_STRUCTURE =
        ResourceKey.create(Registries.STRUCTURE,
            ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "wizard_tower_overworld"));

    private static final int PORTAL_HEIGHT = 2;

    private static final String MARKER_TAG = "sdv_portal_marker:wizard_tower_overworld";
    private static final String TARGET_TAG = "sdv_portal_target:wizard_tower_overworld_enter";

    private static final Set<Long> portalPlacedKeys = new java.util.HashSet<>();
    private static final Map<Long, TowerWork> pending = new java.util.HashMap<>();
    private static final java.util.ArrayDeque<Long> order = new java.util.ArrayDeque<>();

    // 每 100 tick（~5 秒）检测一次
    private static final int CHECK_INTERVAL = 100;

    private WizardTowerStructureHandler() {}

    /**
     * 每次 server 启动清空 in-memory 缓存，确保即使 JVM 未重启（例如单人游戏切换存档），
     * 法师塔传送方块也会在玩家靠近时重新校验并补放，避免残留 key 遮蔽实际丢失的方块。
     */
    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        pending.clear();
        order.clear();
        portalPlacedKeys.clear();
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.tickCount % CHECK_INTERVAL != 0) return;
        if (!Level.OVERWORLD.equals(player.level().dimension())) return;

        ServerLevel level = player.serverLevel();
        BlockPos playerPos = player.blockPosition();

        StructureStart start = findNearbyWizardTower(level, playerPos);
        if (start == null || !start.isValid()) return;

        BoundingBox bb = start.getBoundingBox();

        long key = packBBKey(bb);
        if (portalPlacedKeys.contains(key)) return;
        TowerWork work = pending.get(key);
        if (work == null) {
            work = new TowerWork(level, bb);
            pending.put(key, work);
            order.addLast(key);
        }
        work.lastSeen = level.getServer().getTickCount();
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        pending.clear(); order.clear(); portalPlacedKeys.clear();
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        long started = System.nanoTime();
        for (int reads = 0; reads < 1024 && !order.isEmpty()
                && System.nanoTime() - started < 2_000_000L; reads++) {
            long key = order.removeFirst();
            TowerWork work = pending.get(key);
            if (event.getServer().getTickCount() - work.lastSeen > 200) {
                pending.remove(key);
                continue;
            }
            try {
                work.scan.step(pos -> work.level.getChunkSource()
                        .getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) != null, work::visit);
                if (work.scan.isComplete()) {
                    BlockPos target = work.portal != null ? work.portal : work.door;
                    if (target == null) {
                        pending.remove(key);
                        StardewCraft.LOGGER.warn("[WIZARD_STRUCT] No entrance at {}", work.bounds.getCenter());
                        continue;
                    }
                    if (work.level.getChunkSource().getChunkNow(target.getX() >> 4, target.getZ() >> 4) != null) {
                        if (work.portal == null) InteriorSubspaceManager.placePortalTriggerArea(
                                work.level, target, PORTAL_HEIGHT, 1, 1, MARKER_TAG, TARGET_TAG);
                        pending.remove(key);
                        portalPlacedKeys.add(key);
                        continue;
                    }
                }
                order.addLast(key);
            } catch (RuntimeException failure) {
                pending.remove(key);
                StardewCraft.LOGGER.error("[WIZARD_STRUCT] Deferred entrance scan failed", failure);
            }
        }
    }

    private static final class TowerWork {
        final ServerLevel level;
        final BoundingBox bounds;
        final LoadedColumnScan scan;
        BlockPos portal, door;
        int lastSeen;
        TowerWork(ServerLevel level, BoundingBox bounds) {
            this.level = level;
            this.bounds = bounds;
            scan = new LoadedColumnScan(bounds.minX() - 3, bounds.maxX() + 3,
                    Math.max(level.getMinBuildHeight(), bounds.minY() - 2),
                    Math.min(level.getMaxBuildHeight() - 2, bounds.maxY()),
                    bounds.minZ() - 3, bounds.maxZ() + 3);
        }
        void visit(BlockPos pos) {
            BlockState state = level.getBlockState(pos);
            if (state.is(Blocks.DIRT)) {
                BlockState above = level.getBlockState(pos.above());
                if (above.isAir() || !above.canOcclude()) level.setBlock(pos, Blocks.GRASS_BLOCK.defaultBlockState(), 2);
            }
            if (!bounds.isInside(pos)) return;
            if (state.is(ModBlocks.PORTAL_TRIGGER.get())) portal = pos;
            if (state.is(Blocks.DARK_OAK_DOOR) && state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER
                    && (door == null || pos.getY() < door.getY())) door = pos;
        }
    }

    private static StructureStart findNearbyWizardTower(ServerLevel level, BlockPos playerPos) {
        ChunkPos center = new ChunkPos(playerPos);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                ChunkPos cp = new ChunkPos(center.x + dx, center.z + dz);
                ChunkAccess chunk = level.getChunkSource().getChunkNow(cp.x, cp.z);
                if (chunk == null) continue;
                Map<Structure, StructureStart> starts = chunk.getAllStarts();
                for (Map.Entry<Structure, StructureStart> entry : starts.entrySet()) {
                    StructureStart ss = entry.getValue();
                    if (!ss.isValid()) continue;
                    var registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
                    var key = registry.getKey(entry.getKey());
                    if (key != null && key.equals(WIZARD_TOWER_STRUCTURE.location())) {
                        return ss;
                    }
                }
            }
        }
        return null;
    }

    /** 将包围盒中心编码为 long key */
    private static long packBBKey(BoundingBox bb) {
        int cx = (bb.minX() + bb.maxX()) / 2;
        int cz = (bb.minZ() + bb.maxZ()) / 2;
        return ((long) cx << 32) | (cz & 0xFFFFFFFFL);
    }
}
