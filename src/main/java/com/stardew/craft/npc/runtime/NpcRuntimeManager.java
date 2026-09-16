package com.stardew.craft.npc.runtime;

import com.stardew.craft.core.ModDimensions;
import com.stardew.craft.core.ModMiningDimensions;
import com.stardew.craft.npc.data.NpcCapabilityProfile;
import com.stardew.craft.npc.data.NpcDataRegistry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-server orchestration: definitions, schedule tasks, scene adapters, residency, then locomotion.
 */
@SuppressWarnings("null")
public final class NpcRuntimeManager {
    private static final Map<MinecraftServer, RuntimeSnapshot> SNAPSHOTS = new ConcurrentHashMap<>();

    private NpcRuntimeManager() {
    }

    public static void tickServer(MinecraftServer server) {
        long started=System.nanoTime();
        NpcInteractionService.tickDialogueSessions(server);
        ServerLevel level = server.getLevel(ModDimensions.STARDEW_VALLEY);
        if (level == null) {
            return;
        }

        RuntimeSnapshot snapshot = SNAPSHOTS.computeIfAbsent(server, key -> new RuntimeSnapshot());
        boolean refreshed = refreshFromData(snapshot);
        if (refreshed || !snapshot.syncedRuntimeState) {
            syncRuntimeState(level, snapshot);
            snapshot.syncedRuntimeState = true;
        }

        tickSimulation(server,snapshot);
        long finished=System.nanoTime();
        if(finished-started>=25_000_000L && finished>=snapshot.nextSlowLogNanos) {
            snapshot.nextSlowLogNanos=finished+30_000_000_000L;
            com.mojang.logging.LogUtils.getLogger().warn(
                    "[NPC_RUNTIME_SLOW] totalMs={} scheduleMs={} residencyMs={} movementMs={} configuredActors={}",
                    (finished-started)/1_000_000.0,snapshot.scheduleNanos/1_000_000.0,
                    snapshot.residencyNanos/1_000_000.0,snapshot.movementNanos/1_000_000.0,
                    snapshot.implementedNpcIds.size());
        }
    }

    private static void tickSimulation(MinecraftServer server, RuntimeSnapshot snapshot) {
        snapshot.scheduleNanos=0;snapshot.residencyNanos=0;snapshot.movementNanos=0;
        ServerLevel level = server.getLevel(ModDimensions.STARDEW_VALLEY);
        if (level == null) {
            return;
        }
        NpcSpawnManager.prepareServerContext(level);

        boolean anyPlayerInStardew = false;
        boolean anyPlayerInMining = false;
        for (var player : server.getPlayerList().getPlayers()) {
            if (ModDimensions.STARDEW_VALLEY.equals(player.level().dimension())) {
                anyPlayerInStardew = true;
            }
            if (ModMiningDimensions.STARDEW_MINING.equals(player.level().dimension())) {
                anyPlayerInStardew = true;
                anyPlayerInMining = true;
            }
        }

        if (!anyPlayerInStardew) {
            if (snapshot.hadPlayers) {
                // Save actors at their actual positions, then release this system's chunk leases.
                NpcSpawnManager.onAllPlayersLeft(level);
                NpcChunkForceManager.releaseAllForcedChunks(level);
                // Also release mining dimension forced chunks
                ServerLevel mineLevel = server.getLevel(ModMiningDimensions.STARDEW_MINING);
                if (mineLevel != null) {
                    NpcChunkForceManager.releaseAllForcedChunks(mineLevel);
                }
                NpcScheduleRuntimeService.invalidateCache();
                snapshot.hadPlayers = false;
            }
            // No player in dimension — skip all NPC ticking.
            return;
        }

        if (!snapshot.hadPlayers) {
            // Re-enter the simulation and recover missing actor instances from saved positions.
            NpcScheduleRuntimeService.invalidateCache();
            NpcSpawnManager.onPlayerEntered(level);
            snapshot.hadPlayers = true;
        }

        // Existence and schedule target resolution are lifecycle maintenance, not
        // passage-of-time simulation. They must continue while dialogue/cutscenes
        // pause Stardew time, otherwise a newly entered area can remain NPC-free.
        long stageStarted=System.nanoTime();
        NpcScheduleRuntimeService.tick(level);
        snapshot.scheduleNanos=System.nanoTime()-stageStarted;
        stageStarted=System.nanoTime();
        com.stardew.craft.festival.ActiveFestivalHandlers.tickNpcActors(level);
        NpcSpawnManager.tick(level);

        // Tick mining-dimension NPCs (e.g. Dwarf) when any player is in the mine
        if (anyPlayerInMining) {
            ServerLevel mineLevel = server.getLevel(ModMiningDimensions.STARDEW_MINING);
            if (mineLevel != null) {
                NpcSpawnManager.tickMiningDimension(mineLevel);
            }
        }
        snapshot.residencyNanos=System.nanoTime()-stageStarted;

        if (com.stardew.craft.time.StardewTimePauseService.isPaused(server)) {
            return;
        }
        stageStarted=System.nanoTime();
        NpcCentralMovementService.tick(level);
        snapshot.movementNanos=System.nanoTime()-stageStarted;
    }

    /** World-entry adapters may request lifecycle refresh without introducing another movement tick. */
    public static void ensureActiveWorld(ServerLevel level) {
        var runtime=SNAPSHOTS.computeIfAbsent(level.getServer(),ignored->new RuntimeSnapshot());
        if (!runtime.hadPlayers) {
            NpcScheduleRuntimeService.invalidateCache();
            NpcSpawnManager.onPlayerEntered(level);
            runtime.hadPlayers=true;
        }
    }

    public static void onServerStopped(MinecraftServer server) {
        SNAPSHOTS.remove(server);
    }

    private static boolean refreshFromData(RuntimeSnapshot snapshot) {
        Map<String, NpcCapabilityProfile> capabilities = NpcDataRegistry.capabilities();
        if (snapshot.capabilitiesInitialized && capabilities == snapshot.capabilitiesSnapshot) {
            return false;
        }
        snapshot.capabilitiesSnapshot = capabilities;
        snapshot.capabilitiesInitialized = true;

        snapshot.implementedNpcIds.clear();
        snapshot.pathingNpcIds.clear();

        for (NpcCapabilityProfile profile : capabilities.values()) {
            if (!profile.implemented()) {
                continue;
            }

            snapshot.implementedNpcIds.add(profile.npcId());
            if (profile.canRunPathing()) {
                snapshot.pathingNpcIds.add(profile.npcId());
            }
        }
        return true;
    }

    private static void syncRuntimeState(ServerLevel level, RuntimeSnapshot snapshot) {
        NpcRuntimeDataManager data = NpcRuntimeDataManager.get(level);
        boolean changed = false;

        for (String npcId : snapshot.implementedNpcIds) {
            NpcRuntimeState state = data.getOrCreate(npcId);
            boolean shouldSuppressPathing = !snapshot.pathingNpcIds.contains(npcId);
            if (state.pathingSuppressed() != shouldSuppressPathing) {
                state.setPathingSuppressed(shouldSuppressPathing);
                changed = true;
            }
        }

        if (changed) {
            data.setDirty();
        }
    }

    private static final class RuntimeSnapshot {
        private long nextSlowLogNanos,scheduleNanos,residencyNanos,movementNanos;
        private final Set<String> implementedNpcIds = ConcurrentHashMap.newKeySet();
        private final Set<String> pathingNpcIds = ConcurrentHashMap.newKeySet();
        private Map<String, NpcCapabilityProfile> capabilitiesSnapshot = Map.of();
        private boolean capabilitiesInitialized;
        private boolean syncedRuntimeState;
        private boolean hadPlayers;
    }
}
