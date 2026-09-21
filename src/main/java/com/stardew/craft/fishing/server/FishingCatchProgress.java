package com.stardew.craft.fishing.server;

import com.stardew.craft.player.PlayerStardewDataAPI;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** One source-parity entry point for a fish catch accepted by the player. */
public final class FishingCatchProgress {
    private FishingCatchProgress() {}

    public static void record(ServerPlayer player, ItemStack caught, int count) {
        if (player == null || caught == null || caught.isEmpty() || count <= 0) return;
        String itemId = BuiltInRegistries.ITEM.getKey(caught.getItem()).toString();
        if (itemId.equals("stardewcraft:ancient_doll")
                && com.stardew.craft.fishing.data.FishingDataManager.isFourCornersFishingRegion(
                player.serverLevel(), player.blockPosition())) {
            var data = PlayerStardewDataAPI.getData(player);
            if (data.hasMailFlag("cursed_doll") && !data.hasMailFlag("eric's_prank_1")) {
                data.addMailFlag("eric's_prank_1");
            }
        }
        PlayerStardewDataAPI.addFishCatchCount(player, itemId, count);
        com.stardew.craft.festival.squid.SquidFestService.onFishCaught(player, caught, count);
        com.stardew.craft.quest.StardewQuestEvents.fireFishCaught(player, itemId, count);
        com.stardew.craft.specialorder.SpecialOrderManager.recordFishCaught(player, caught, count);
    }
}
