package com.stardew.craft.warp;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.farm.FarmInstance;
import com.stardew.craft.farm.FarmInstanceRegistry;
import com.stardew.craft.network.ObjectDialogueService;
import com.stardew.craft.sound.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;

import java.util.Random;

/**
 * 传送魔杖的粒子和音效工具类。
 * <p>
 * 复刻 SDV Wand.cs 的视觉效果，复用已有的 TeleportTotemItem 粒子模式。
 */
public final class WarpEffects {

    private static final Random RANDOM = new Random();

    private WarpEffects() {}

    /**
     * 执行传送：出发地粒子 → 传送 → 到达地粒子。
     */
    @SuppressWarnings("null")
    public static void teleport(ServerPlayer player, WarpDestination dest) {
        ServerLevel departureLevel = player.serverLevel();

        FarmInstance targetFarm = null;
        if (dest.requiresPlayerFarm()) {
            targetFarm = FarmInstanceRegistry.get().getFarmForPlayer(player.getUUID());
            if (targetFarm == null) {
                ObjectDialogueService.show(player, "stardewcraft.warp.farm.unavailable");
                StardewCraft.LOGGER.info("[WARP] Player {} tried to warp to farm without a farm",
                        player.getName().getString());
                return;
            }
        }

        // 出发地效果
        spawnWarpParticles(departureLevel, player.getX(), player.getY(), player.getZ());
        departureLevel.playSound(null, player.blockPosition(),
                ModSounds.WAND.get(), SoundSource.PLAYERS, 1.0f, 1.0f);

        // 传送到目标维度
        ResourceKey<Level> targetDim = dest.dimension();
        ServerLevel targetLevel = player.server.getLevel(targetDim);
        if (targetLevel == null) {
            StardewCraft.LOGGER.error("[WARP] Target dimension {} not found!", targetDim.location());
            return;
        }

        // 农场特殊处理：传送到玩家自己的农场出生点
        double tx = dest.x(), ty = dest.y(), tz = dest.z();
        if (targetFarm != null) {
            BlockPos spawn = targetFarm.getSpawnPoint();
            tx = spawn.getX() + 0.5;
            ty = spawn.getY();
            tz = spawn.getZ() + 0.5;
        }

        ModTeleport.to(player, targetLevel, tx, ty, tz, 180.0f, 0.0f);
        player.setDeltaMovement(0, 0, 0);
        player.fallDistance = 0;
        player.hurtMarked = true;

        // 到达地效果
        spawnWarpParticles(targetLevel, tx, ty, tz);
        targetLevel.playSound(null, player.blockPosition(),
                ModSounds.WARRIOR.get(), SoundSource.PLAYERS, 1.0f, 1.0f);

        StardewCraft.LOGGER.info("[WARP] Player {} warped to '{}' at ({}, {}, {})",
                player.getName().getString(), dest.id(), tx, ty, tz);
    }

    /**
     * 在指定位置生成传送粒子。
     * 复用 TeleportTotemItem 的粒子模式：
     * - END_ROD × 12（随机散布 ±4 格，Y+1）
     * - FIREWORK × 24（6 格半径，Y+0.5）
     * - END_ROD 水平扫描（X+8 到 X-8）
     */
    @SuppressWarnings("null")
    public static void spawnWarpParticles(ServerLevel level, double x, double y, double z) {
        // 散射 END_ROD
        for (int i = 0; i < 12; i++) {
            double px = x + (RANDOM.nextDouble() * 8.0 - 4.0);
            double py = y + 1.0 + (RANDOM.nextDouble() * 2.0);
            double pz = z + (RANDOM.nextDouble() * 8.0 - 4.0);
            level.sendParticles(ParticleTypes.END_ROD, px, py, pz, 1, 0, 0, 0, 0);
        }

        // 环形 FIREWORK
        for (int i = 0; i < 24; i++) {
            double angle = (Math.PI * 2.0 / 24) * i;
            double radius = 3.0 + RANDOM.nextDouble() * 3.0;
            double px = x + Math.cos(angle) * radius;
            double pz = z + Math.sin(angle) * radius;
            level.sendParticles(ParticleTypes.FIREWORK, px, y + 0.5, pz, 1, 0, 0.1, 0, 0.02);
        }

        // 水平扫描 END_ROD
        for (int j = -8; j <= 8; j++) {
            level.sendParticles(ParticleTypes.END_ROD, x + j, y + 0.5, z, 1, 0, 0, 0, 0);
        }
    }
}
