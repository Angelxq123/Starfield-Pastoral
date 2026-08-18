package com.stardew.craft.event;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.casino.CasinoAccessService;
import com.stardew.craft.core.ModDimensions;
import com.stardew.craft.core.ModMiningDimensions;
import com.stardew.craft.desert.DesertGalaxyPillarBootstrap;
import com.stardew.craft.joja.JojaNpcEvents;
import com.stardew.craft.mining.MineEntranceBootstrap;
import com.stardew.craft.qi.MrQiQuestInteractionService;
import com.stardew.craft.secretnote.SecretNote20Service;
import com.stardew.craft.secretnote.SecretNoteFurnitureService;
import com.stardew.craft.shop.PrizeTicketMachineInstaller;
import com.stardew.craft.totem.SystemTotemManager;
import com.stardew.craft.world.OldMasterCannoliService;
import com.stardew.craft.world.MutantBugLairService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Preloads fixed public structures before player travel can become their normal trigger. */
@EventBusSubscriber(modid = StardewCraft.MODID)
public final class FixedPublicAreaStartupPreloader {
    private static final int CHUNKS_PER_TICK = 2;
    private static final StartupChunkPreloadQueue<ServerLevel> QUEUE =
            new StartupChunkPreloadQueue<>(new StartupChunkPreloadQueue.Backend<>() {
                @Override
                public boolean isForced(ServerLevel level, ChunkPos chunk) {
                    return level.getForcedChunks().contains(chunk.toLong());
                }

                @Override
                public boolean acquire(ServerLevel level, ChunkPos chunk) {
                    if (level.getForcedChunks().contains(chunk.toLong())) {
                        return false;
                    }
                    return level.setChunkForced(chunk.x, chunk.z, true);
                }

                @Override
                public boolean isLoaded(ServerLevel level, ChunkPos chunk) {
                    return level.getChunkSource().getChunkNow(chunk.x, chunk.z) != null;
                }

                @Override
                public void release(ServerLevel level, ChunkPos chunk) {
                    level.setChunkForced(chunk.x, chunk.z, false);
                }

                @Override
                public void onFailure(String id, RuntimeException exception) {
                    StardewCraft.LOGGER.error("[STARTUP_PRELOAD] Work '{}' failed", id, exception);
                }
            }, CHUNKS_PER_TICK, CHUNKS_PER_TICK);

    private FixedPublicAreaStartupPreloader() {
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        ServerLevel mines = event.getServer().getLevel(ModMiningDimensions.STARDEW_MINING);
        if (mines != null) {
            QUEUE.enqueue("mine_lobby", mines, mineLobbyChunks(), () -> {
                MineEntranceBootstrap.ensureGenerated(mines);
                StardewCraft.LOGGER.info("[STARTUP_PRELOAD] Mine lobby ready");
            });
        }

        ServerLevel valley = event.getServer().getLevel(ModDimensions.STARDEW_VALLEY);
        if (valley != null) {
            QUEUE.enqueue("fixed_public_interactions", valley, fixedPublicChunks(), () -> {
                DesertGalaxyPillarBootstrap.ensurePlaced(valley, "startup_preload");
                PrizeTicketMachineInstaller.ensurePlaced(valley);
                CasinoAccessService.install(valley);
                MrQiQuestInteractionService.install(valley);
                SecretNote20Service.ensureInteractions(valley);
                SecretNoteFurnitureService.ensureStoneJunimoPlaced(valley);
                com.stardew.craft.event.LuckyPurpleShortsWorldEvents.ensurePlaced(valley);
                OldMasterCannoliService.install(valley);
                SystemTotemManager.installFixedTotems(valley);
                JojaNpcEvents.forceCheckNow(valley);
                MutantBugLairService.initializeLoadedFixedContent(valley);
                StardewCraft.LOGGER.info("[STARTUP_PRELOAD] Fixed public interactions ready");
            });
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        QUEUE.tick();
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        QUEUE.clear();
    }

    static List<ChunkPos> mineLobbyChunks() {
        return chunksCovering(new BlockPos(-20, 64, -20), new BlockPos(19, 96, 19));
    }

    static List<ChunkPos> fixedPublicChunks() {
        Set<ChunkPos> chunks = new LinkedHashSet<>();
        addArea(chunks, PrizeTicketMachineInstaller.MACHINE_POS, PrizeTicketMachineInstaller.MACHINE_POS);
        addArea(chunks, CasinoAccessService.ENTRY_MIN, CasinoAccessService.ENTRY_MAX);
        addArea(chunks, CasinoAccessService.EXIT_MIN, CasinoAccessService.EXIT_MAX);
        addArea(chunks, CasinoAccessService.QI_COIN_MACHINE_MIN, CasinoAccessService.QI_COIN_MACHINE_MAX);
        addArea(chunks, CasinoAccessService.QI_COIN_SHOP_MIN, CasinoAccessService.QI_COIN_SHOP_MAX);
        addArea(chunks, MrQiQuestInteractionService.TUNNEL_SAFE_POS, MrQiQuestInteractionService.TUNNEL_SAFE_POS);
        addArea(chunks, MrQiQuestInteractionService.SAND_DRAGON_POS, MrQiQuestInteractionService.SAND_DRAGON_POS);
        addArea(chunks, SecretNote20Service.TRUCK_MIN, SecretNote20Service.TRUCK_MAX);
        addArea(chunks, SecretNoteFurnitureService.STONE_JUNIMO_POS,
                SecretNoteFurnitureService.STONE_JUNIMO_POS.above());
        addArea(chunks, OldMasterCannoliService.INTERACTION_MIN, OldMasterCannoliService.INTERACTION_MAX);
        addArea(chunks, DesertGalaxyPillarBootstrap.RITUAL_TRIGGER_POS,
                DesertGalaxyPillarBootstrap.RITUAL_TRIGGER_POS);
        chunks.addAll(JojaNpcEvents.fixedSpawnChunks());
        addArea(chunks, MutantBugLairService.ENTRANCE_PORTAL_BASE,
                MutantBugLairService.ENTRANCE_PORTAL_BASE.offset(1, 0, 0));
        addArea(chunks, MutantBugLairService.EXIT_PORTAL_BASE,
                MutantBugLairService.EXIT_PORTAL_BASE.offset(1, 0, 0));
        addArea(chunks, MutantBugLairService.REWARD_CHEST_POS,
                MutantBugLairService.REWARD_CHEST_POS);

        addArea(chunks, new BlockPos(-86, 34, 12), new BlockPos(-86, 34, 12));
        addArea(chunks, new BlockPos(68, 44, 20), new BlockPos(81, 45, 32));
        addArea(chunks, new BlockPos(135, -12, 136), new BlockPos(135, -12, 136));
        addArea(chunks, new BlockPos(-290, -14, 256), new BlockPos(-290, -14, 256));
        addArea(chunks, new BlockPos(75, 81, -105), new BlockPos(75, 81, -105));
        addArea(chunks, new BlockPos(52, 88, -128), new BlockPos(52, 88, -128));
        addArea(chunks, new BlockPos(44, 60, 93), new BlockPos(44, 60, 93));
        addArea(chunks, new BlockPos(-203, 64, -157), new BlockPos(-203, 64, -157));
        return List.copyOf(chunks);
    }

    private static List<ChunkPos> chunksCovering(BlockPos min, BlockPos max) {
        Set<ChunkPos> chunks = new LinkedHashSet<>();
        addArea(chunks, min, max);
        return List.copyOf(chunks);
    }

    private static void addArea(Set<ChunkPos> chunks, BlockPos first, BlockPos second) {
        int minChunkX = Math.min(first.getX(), second.getX()) >> 4;
        int maxChunkX = Math.max(first.getX(), second.getX()) >> 4;
        int minChunkZ = Math.min(first.getZ(), second.getZ()) >> 4;
        int maxChunkZ = Math.max(first.getZ(), second.getZ()) >> 4;
        for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
            for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
                chunks.add(new ChunkPos(chunkX, chunkZ));
            }
        }
    }
}
