package com.stardew.craft.mastery.effect;

import com.stardew.craft.sound.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;

/** SDV radius-three ring of ordinary surface markers, sharing the natural placement rules. */
public final class TreasureTotemService {

    private TreasureTotemService() {}

    private static final int RADIUS = 4;
    private static final int RING_DISTANCE = RADIUS - 1; // == 3

    @SuppressWarnings("null")
    public static void activate(Player player) {
        if (!(player.level() instanceof ServerLevel level)) return;

        BlockPos playerPos = player.blockPosition();
        if (!canActivateAt(player)) {
            // 不在任何采集区域 — 播放 cancel 音，不消耗物品（调用方需要据此回收）
            level.playSound(null, playerPos, SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.PLAYERS, 0.6f, 0.6f);
            return;
        }

        level.playSound(null, playerPos, ModSounds.TREASURE_TOTEM.get(), SoundSource.PLAYERS, 1.0f, 1.0f);

        com.stardew.craft.manager.ArtifactSpotSpawnService.recordTreasureTotem(level);
        int cx = playerPos.getX();
        int cz = playerPos.getZ();
        int footY = playerPos.getY() - 1; // 玩家脚底那层

        for (int dx = -RADIUS; dx < RADIUS; dx++) {
            for (int dz = -RADIUS; dz < RADIUS; dz++) {
                int dist = (int) Math.round(Math.sqrt(dx * dx + dz * dz));
                int x = cx + dx;
                int z = cz + dz;

                if (dist < RING_DISTANCE) {
                    spawnSpiralParticles(level, x, footY, z);
                    continue;
                }
                if (dist != RING_DISTANCE) continue;

                BlockPos foot = new BlockPos(x, footY, z);
                if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer
                        && !com.stardew.craft.manager.ArtifactSpotDigService.allowed(serverPlayer, foot)) continue;
                if (!com.stardew.craft.manager.ArtifactSpotSpawnService.locationName(level, foot)
                        .equals(com.stardew.craft.manager.ArtifactSpotSpawnService.locationName(level, playerPos.below()))) continue;
                if (!com.stardew.craft.manager.ArtifactSpotSpawnService.place(level, foot.above(), false)) continue;

                spawnReplaceParticles(level, x, footY + 1, z);
            }
        }
    }

    public static boolean canActivateAt(Player player) {
        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer
                && !com.stardew.craft.manager.ArtifactSpotDigService.allowed(serverPlayer, player.blockPosition())) return false;
        return !"Default".equals(com.stardew.craft.manager.ArtifactSpotSpawnService
                .locationName(player.level(), player.blockPosition().below()));
    }

    private static void spawnReplaceParticles(ServerLevel level, int x, int y, int z) {
        level.sendParticles(ParticleTypes.END_ROD,
            x + 0.5, y + 0.1, z + 0.5,
            6, 0.2, 0.4, 0.2, 0.04);
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER,
            x + 0.5, y + 0.3, z + 0.5,
            3, 0.3, 0.2, 0.3, 0);
    }

    private static void spawnSpiralParticles(ServerLevel level, int x, int y, int z) {
        if (level.getRandom().nextFloat() < 0.5f) {
            level.sendParticles(ParticleTypes.ENCHANT,
                x + 0.5, y + 0.8, z + 0.5,
                2, 0.2, 0.2, 0.2, 0.4);
        }
    }
}
