package com.stardew.craft.specialorder;

import com.stardew.craft.item.ModItems;
import com.stardew.craft.player.PlayerStardewDataAPI;
import com.stardew.craft.sound.ModSounds;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;

/** SDV SpecialOrdersPrizeTickets: one personal ticket per click, only when it fits. */
public final class SpecialOrderTicketService {
    private SpecialOrderTicketService() {}

    public static boolean claimOne(ServerPlayer player) {
        if (!SpecialOrderManager.isUnlockedFor(player)
                || PlayerStardewDataAPI.getSpecialOrderPrizeTickets(player) == 0) return false;
        ItemStack ticket = new ItemStack(ModItems.PRIZE_TICKET.get());
        var inventory = player.getInventory();
        // Inventory.add discards overflow for creative players: preflight before touching the counter.
        if (inventory.getFreeSlot() < 0 && inventory.getSlotWithRemainingSpace(ticket) < 0) {
            player.displayClientMessage(Component.translatable("stardewcraft.geode.inventory_full"), true);
            return false;
        }
        if (!inventory.add(ticket)) return false;
        PlayerStardewDataAPI.consumeSpecialOrderPrizeTicket(player);
        player.containerMenu.broadcastChanges();
        player.playNotifySound(ModSounds.COIN.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
        return true;
    }
}
