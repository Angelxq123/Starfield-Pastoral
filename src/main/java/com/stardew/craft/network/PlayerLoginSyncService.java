package com.stardew.craft.network;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.api.v1.internal.festival.StardewFestivalSessionSyncService;
import com.stardew.craft.communitycenter.network.BundleSyncPayload;
import com.stardew.craft.communitycenter.reward.panning.OrePanPointManager;
import com.stardew.craft.network.payload.RequestNpcFriendshipOverviewPayload;
import com.stardew.craft.player.PlayerDataManager;
import com.stardew.craft.player.PlayerStardewData;
import com.stardew.craft.player.CosmeticAppearanceSync;
import com.stardew.craft.quest.network.QuestLogSyncPayload;
import com.stardew.craft.server.performance.PerformanceCounter;
import com.stardew.craft.server.performance.PerformanceTiming;
import com.stardew.craft.server.performance.ServerPerformanceRecorder;
import com.stardew.craft.specialorder.SpecialOrderManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.UUID;

/** Smooths player-specific login snapshots across several server ticks. */
@EventBusSubscriber(modid = StardewCraft.MODID)
public final class PlayerLoginSyncService {
    private static final int MAX_STAGES_PER_TICK = 4;
    private static final Queue<PendingSync> PENDING_SYNCS = new ArrayDeque<>();

    private PlayerLoginSyncService() {
    }

    public static void enqueue(ServerPlayer player) {
        MinecraftServer server = player.server;
        UUID playerId = player.getUUID();
        PENDING_SYNCS.removeIf(pending -> pending.server == server
                && pending.playerId.equals(playerId));
        PENDING_SYNCS.add(new PendingSync(server, playerId));
        ServerPerformanceRecorder.increment(
                PerformanceCounter.PLAYER_LOGIN_SYNC_ENQUEUED, 1L);
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        int stagesThisTick = Math.min(MAX_STAGES_PER_TICK, PENDING_SYNCS.size());
        for (int index = 0; index < stagesThisTick; index++) {
            PendingSync pending = PENDING_SYNCS.poll();
            if (pending == null) {
                return;
            }
            ServerPlayer player = pending.server.getPlayerList().getPlayer(pending.playerId);
            if (player == null) {
                continue;
            }
            if (pending.sendNext(player)) {
                PENDING_SYNCS.add(pending);
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PENDING_SYNCS.removeIf(pending -> pending.server == player.server
                    && pending.playerId.equals(event.getEntity().getUUID()));
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        PENDING_SYNCS.removeIf(pending -> pending.server == event.getServer());
    }

    private enum SyncStage {
        COSMETICS,
        FESTIVAL_SESSIONS,
        COMMUNITY_CENTER,
        PAN_POINT,
        NPC_FRIENDSHIP,
        QUEST_LOG,
        SPECIAL_ORDERS
    }

    private static final class PendingSync {
        private final MinecraftServer server;
        private final UUID playerId;
        private SyncStage stage = SyncStage.COSMETICS;

        private PendingSync(MinecraftServer server, UUID playerId) {
            this.server = server;
            this.playerId = playerId;
        }

        private boolean sendNext(ServerPlayer player) {
            switch (stage) {
                case COSMETICS -> {
                    runStage(player, "cosmetic appearances", () ->
                            CosmeticAppearanceSync.syncAllTo(player));
                    stage = SyncStage.FESTIVAL_SESSIONS;
                    return true;
                }
                case FESTIVAL_SESSIONS -> {
                    runStage(player, "festival sessions", () ->
                            StardewFestivalSessionSyncService.syncToPlayer(player));
                    stage = SyncStage.COMMUNITY_CENTER;
                    return true;
                }
                case COMMUNITY_CENTER -> {
                    runStage(player, "community center", () ->
                            BundleSyncPayload.sendFullSync(player));
                    stage = SyncStage.PAN_POINT;
                    return true;
                }
                case PAN_POINT -> {
                    runStage(player, "ore-pan point", () ->
                            OrePanPointManager.get(player.serverLevel()).syncToClient(player));
                    stage = SyncStage.NPC_FRIENDSHIP;
                    return true;
                }
                case NPC_FRIENDSHIP -> {
                    runStage(player, "NPC friendship", () ->
                            RequestNpcFriendshipOverviewPayload.sendOverviewTo(player));
                    stage = SyncStage.QUEST_LOG;
                    return true;
                }
                case QUEST_LOG -> {
                    runStage(player, "quest log", () -> {
                        PlayerStardewData data = PlayerDataManager.getPlayerData(player);
                        var quests = data.getQuestManager();
                        PacketDistributor.sendToPlayer(player, QuestLogSyncPayload.fromQuests(
                                quests.getQuestLog(),
                                quests.getBillboardQuestsDone(),
                                quests.getDailyQuestCompletedDays()));
                    });
                    stage = SyncStage.SPECIAL_ORDERS;
                    return true;
                }
                case SPECIAL_ORDERS -> {
                    runStage(player, "special orders", () ->
                            SpecialOrderManager.syncState(player));
                    ServerPerformanceRecorder.increment(
                            PerformanceCounter.PLAYER_LOGIN_SYNC_COMPLETED, 1L);
                    return false;
                }
            }
            throw new IllegalStateException("Unknown login sync stage " + stage);
        }
    }

    private static void runStage(ServerPlayer player, String name, Runnable operation) {
        long startedAt = ServerPerformanceRecorder.startTiming();
        try {
            operation.run();
        } catch (RuntimeException exception) {
            StardewCraft.LOGGER.warn(
                    "Failed to sync {} during login for {}",
                    name,
                    player.getGameProfile().getName(),
                    exception);
        } finally {
            ServerPerformanceRecorder.finishTiming(
                    PerformanceTiming.PLAYER_LOGIN_SYNC_STAGE, startedAt);
            ServerPerformanceRecorder.increment(
                    PerformanceCounter.PLAYER_LOGIN_SYNC_STAGES, 1L);
        }
    }
}
