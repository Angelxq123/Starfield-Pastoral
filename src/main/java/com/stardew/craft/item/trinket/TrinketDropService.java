package com.stardew.craft.item.trinket;

import com.stardew.craft.player.PlayerDataManager;
import com.stardew.craft.player.PlayerStardewData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;

import java.util.Collection;
import java.util.Set;

public final class TrinketDropService {
    private TrinketDropService() {
    }

    public static boolean canSpawnTrinket(ServerPlayer player) {
        return StardewTrinketItem.canSpawnFor(player);
    }

    public static void tryAddMonsterDrop(Collection<ItemEntity> drops, LivingEntity monster, ServerPlayer player, RandomSource random) {
        if (!rollSpawn(player, random, chanceForMonster(monster, player, 1.0))) {
            return;
        }
        ItemStack trinket = StardewTrinketItem.createRandomNaturalTrinket(random, player);
        if (!trinket.isEmpty()) {
            drops.add(new ItemEntity(monster.level(), monster.getX(), monster.getY() + 0.25, monster.getZ(), trinket));
        }
    }

    public static void trySpawnContainerDrop(ServerLevel level, BlockPos pos, double chanceModifier) {
        trySpawnContainerDrop(level, pos, chanceModifier, nearestEligiblePlayer(level, Vec3.atCenterOf(pos)));
    }

    public static void trySpawnContainerDrop(ServerLevel level, BlockPos pos, double chanceModifier, ServerPlayer player) {
        if (player == null || !rollSpawn(player, level.getRandom(), chanceForMonster(null, player, chanceModifier))) {
            return;
        }
        ItemStack trinket = StardewTrinketItem.createRandomNaturalTrinket(level.getRandom(), player);
        if (!trinket.isEmpty()) {
            Block.popResource(level, pos, trinket);
        }
    }

    private static double chanceForMonster(LivingEntity monster, ServerPlayer player, double chanceModifier) {
        double baseChance = 0.004;
        if (monster != null) {
            baseChance += monster.getMaxHealth() * 0.00001;
            Set<String> tags = monster.getTags();
            if (isGlider(tags) && monster.getMaxHealth() >= 150.0f) {
                baseChance += 0.002;
            }
            if (tags.contains("sd_mob_leaper")) {
                baseChance -= 0.005;
            }
        }
        baseChance = Math.min(0.025, baseChance);
        PlayerStardewData data = PlayerDataManager.getPlayerData(player);
        baseChance += data.getDailyLuck() / 25.0;
        baseChance += data.getLuckLevel() * 0.00133f;
        return baseChance * chanceModifier;
    }

    private static boolean rollSpawn(ServerPlayer player, RandomSource random, double chance) {
        return canSpawnTrinket(player) && random.nextDouble() < chance;
    }

    private static boolean isGlider(Set<String> tags) {
        return tags.contains("sd_mob_bat")
                || tags.contains("sd_mob_fly")
                || tags.contains("sd_mob_serpent")
                || tags.contains("sd_mob_royal_serpent");
    }

    private static ServerPlayer nearestEligiblePlayer(ServerLevel level, Vec3 pos) {
        ServerPlayer best = null;
        double bestDistance = Double.MAX_VALUE;
        for (ServerPlayer player : level.players()) {
            if (!canSpawnTrinket(player)) {
                continue;
            }
            double distance = player.distanceToSqr(pos);
            if (distance < bestDistance) {
                best = player;
                bestDistance = distance;
            }
        }
        return best;
    }
}