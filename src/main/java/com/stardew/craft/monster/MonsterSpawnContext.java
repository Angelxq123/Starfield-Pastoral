package com.stardew.craft.monster;

import com.stardew.craft.mining.MineFloorData;
import com.stardew.craft.mining.MiningDataManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import java.util.UUID;

/** Snapshot of creation inputs, not a lookup against whichever player happens to be nearby later. */
public record MonsterSpawnContext(Source source, int floor, boolean bottomReached, UUID generation) {
    public enum Source { COMMAND, ORDINARY_MINE, SKULL_CAVERN, WORLD, OFFSPRING }
    public MonsterSpawnContext {
        if (floor < 1) throw new IllegalArgumentException("Monster source floor must be positive");
    }
    public static MonsterSpawnContext capture(ServerLevel level, Source source, int floor) {
        boolean bottom = level.getServer().getPlayerList().getPlayers().stream()
                .anyMatch(p -> MiningDataManager.getPlayerData(p).getMaxFloorReached() >= 120);
        return new MonsterSpawnContext(source, Math.max(1, floor), bottom, null);
    }
    public static MonsterSpawnContext mine(ServerLevel level, int floor, MineFloorData data) {
        var context = capture(level, floor > 120 ? Source.SKULL_CAVERN : Source.ORDINARY_MINE, floor);
        return new MonsterSpawnContext(context.source, context.floor, context.bottomReached, data.generationId());
    }
    public MonsterSpawnContext offspring() { return new MonsterSpawnContext(Source.OFFSPRING, floor, bottomReached, generation); }
    public CompoundTag save() {
        var tag = new CompoundTag();
        tag.putString("Source", source.name()); tag.putInt("Floor", floor); tag.putBoolean("BottomReached", bottomReached);
        if (generation != null) tag.putUUID("Generation", generation);
        return tag;
    }
    public static MonsterSpawnContext load(CompoundTag tag) {
        return new MonsterSpawnContext(Source.valueOf(tag.getString("Source")), tag.getInt("Floor"),
                tag.getBoolean("BottomReached"), tag.hasUUID("Generation") ? tag.getUUID("Generation") : null);
    }
}
