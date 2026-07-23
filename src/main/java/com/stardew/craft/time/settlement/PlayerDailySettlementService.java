package com.stardew.craft.time.settlement;

import com.stardew.craft.core.ModDimensions;
import com.stardew.craft.core.ModMiningDimensions;
import com.stardew.craft.network.overnight.OvernightSettlementPayload;
import com.stardew.craft.network.overnight.OvernightSettlementTracker;
import com.stardew.craft.player.PassOutService;
import com.stardew.craft.player.PlayerStardewData;
import com.stardew.craft.player.PlayerStardewDataAPI;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PlayerDailySettlementService {
    private final WeakReference<MinecraftServer> server;
    private final Map<UUID, DailySettlementBarrier.ReadyResult> readyResults =
            new ConcurrentHashMap<>();

    public PlayerDailySettlementService(MinecraftServer server) {
        this.server = new WeakReference<>(Objects.requireNonNull(server, "server"));
    }

    public boolean participates(ServerPlayer player) {
        Objects.requireNonNull(player, "player");
        return (player.level().dimension() == ModDimensions.STARDEW_VALLEY
                || player.level().dimension() == ModMiningDimensions.STARDEW_MINING)
                && com.stardew.craft.farm.FarmInstanceRegistry.get().hasFarm(player.getUUID());
    }

    public DailySettlementWorkUnit createDailyWorkUnit(DailySettlementContext context) {
        Objects.requireNonNull(context, "context");
        return DailySettlementWorkUnits.cursor(
                "player_daily_settlement",
                context.playerIds(),
                UUID::toString,
                playerId -> settlePlayer(context, playerId),
                () -> {});
    }

    void settlePlayer(DailySettlementContext context, UUID playerId) {
        ServerPlayer player = server().getPlayerList().getPlayer(playerId);
        OvernightSettlementPayload payload;
        if (player == null) {
            payload = settleDisconnectedPlayer(context, playerId);
        } else {
            payload = settleOnlinePlayer(context, player);
        }
        readyResults.put(playerId,
                new DailySettlementBarrier.ReadyResult(context.absoluteDay(), payload));
    }

    OvernightSettlementPayload settleOnlinePlayer(
            DailySettlementContext context, ServerPlayer player) {
        if (player.isCreative()) {
            PlayerStardewDataAPI.cureExhaustion(player);
            PlayerStardewDataAPI.restoreEnergy(player, PlayerStardewDataAPI.getMaxEnergy(player));
            com.stardew.craft.mastery.MasteryBuffLifecycle.clearAllDailyMasteryBuffs(player);
        } else {
            PlayerStardewDataAPI.sleep(player, context.sleepMinute());
            PassOutService.applyCombatDeathEnergyPenalty(player);
        }
        PlayerStardewDataAPI.setHealth(player, PlayerStardewDataAPI.getMaxHealth(player));

        com.stardew.craft.shop.BlacksmithService.onNewDay(player);
        com.stardew.craft.shop.BlacksmithService.showToolUpgradeNotification(player);

        OvernightSettlementPayload settlementPayload = OvernightSettlementTracker.consumePayload(player);
        PlayerStardewDataAPI.recordOvernightShippedItems(player, settlementPayload.shippedItems());
        List<PlayerStardewData.SkillLevelUp> appliedLevelUps =
                PlayerStardewDataAPI.applyPendingSkillLevelUps(player);
        PlayerStardewDataAPI.applySkillLevelRecipeUnlocks(player, appliedLevelUps);
        if (!appliedLevelUps.isEmpty()) {
            PlayerStardewDataAPI.restoreEnergy(player, PlayerStardewDataAPI.getMaxEnergy(player));
            PlayerStardewDataAPI.setHealth(player, PlayerStardewDataAPI.getMaxHealth(player));
        }

        com.stardew.craft.player.PlayerDataEventHandler.syncPlayerData(
                player, com.stardew.craft.player.PlayerDataManager.getPlayerData(player));
        com.stardew.craft.quest.StardewQuestEvents.fireDayStarted(player, context.absoluteDay());
        com.stardew.craft.mastery.MasteryOnboardingService.checkOnMorning(player);
        return buildPayload(context, player.getUUID(), settlementPayload, appliedLevelUps);
    }

    private OvernightSettlementPayload settleDisconnectedPlayer(
            DailySettlementContext context, UUID playerId) {
        OvernightSettlementPayload settlementPayload = OvernightSettlementTracker.consumePayload(
                server(), playerId, context.absoluteDay());
        return buildPayload(context, playerId, settlementPayload, List.of());
    }

    private OvernightSettlementPayload buildPayload(
            DailySettlementContext context,
            UUID playerId,
            OvernightSettlementPayload settlementPayload,
            List<PlayerStardewData.SkillLevelUp> appliedLevelUps) {
        List<OvernightSettlementPayload.LevelUpData> levelUps =
                new ArrayList<>(settlementPayload.levelUps());
        for (PlayerStardewData.SkillLevelUp levelUp : appliedLevelUps) {
            levelUps.add(new OvernightSettlementPayload.LevelUpData(
                    levelUp.skill().getId(), levelUp.newLevel()));
        }

        PassOutService.PassOutResult passOutResult =
                PassOutService.consumePassOutResult(playerId, context.absoluteDay());
        int passOutType = passOutResult == null ? -1 : passOutResult.type().getId();
        int moneyLost = passOutResult == null ? 0 : passOutResult.moneyLost();
        List<net.minecraft.world.item.ItemStack> lostItems =
                passOutResult == null ? List.of() : passOutResult.lostItems();
        return new OvernightSettlementPayload(
                context.absoluteDay(),
                settlementPayload.shippedItems(),
                List.copyOf(levelUps),
                passOutType,
                moneyLost,
                lostItems);
    }

    public DailySettlementBarrier.ReadyResult readyResult(UUID playerId, int absoluteDay) {
        DailySettlementBarrier.ReadyResult result = readyResults.get(playerId);
        return result != null && result.absoluteDay() == absoluteDay ? result : null;
    }

    DailySettlementBarrier.ReadyResult readyResultOrCreate(
            DailySettlementContext context, UUID playerId) {
        DailySettlementBarrier.ReadyResult result = readyResult(
                playerId, context.absoluteDay());
        if (result != null) {
            return result;
        }
        OvernightSettlementPayload payload = settleDisconnectedPlayer(context, playerId);
        DailySettlementBarrier.ReadyResult fallback =
                new DailySettlementBarrier.ReadyResult(context.absoluteDay(), payload);
        readyResults.put(playerId, fallback);
        return fallback;
    }

    public void onLogin(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
    }

    public void onLogout(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
    }

    public void clear() {
        readyResults.clear();
    }

    private MinecraftServer server() {
        MinecraftServer current = server.get();
        if (current == null) {
            throw new IllegalStateException("Daily settlement server is no longer available");
        }
        return current;
    }
}
