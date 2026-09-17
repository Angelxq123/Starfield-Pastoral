package com.stardew.craft.inventory;

import com.stardew.craft.api.v1.item.StardewItemDataApi;
import com.stardew.craft.economy.sell.ProfessionSellPriceService;
import com.stardew.craft.economy.sell.SellQuote;
import com.stardew.craft.economy.sell.SellSource;
import com.stardew.craft.item.cosmetic.StardewCosmeticItem;
import com.stardew.craft.item.equipment.CombinedRingItem;
import com.stardew.craft.item.equipment.StardewBootsItem;
import com.stardew.craft.item.equipment.StardewRingItem;
import com.stardew.craft.item.tool.FishingRodItem;
import com.stardew.craft.item.tool.PanItem;
import com.stardew.craft.item.trinket.StardewTrinketItem;
import com.stardew.craft.item.weapon.SlingshotItem;
import com.stardew.craft.item.weapon.StardewClubItem;
import com.stardew.craft.item.weapon.StardewDaggerItem;
import com.stardew.craft.item.weapon.StardewWeaponItem;
import com.stardew.craft.money.SharedMoneyService;
import com.stardew.craft.player.PlayerDataManager;
import com.stardew.craft.player.PlayerStardewData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Set;

/** Server-authoritative trashing and reclaim calculation. */
public final class TrashCanService {
    private static final Set<String> NON_RECLAIMABLE_TYPES = Set.of(
            "stardewcraft.type.tool",
            "stardewcraft.type.weapon.slingshot",
            "stardewcraft.type.furniture",
            "stardewcraft.type.special_furniture",
            "stardewcraft.type.furniture_painting",
            "stardewcraft.type.festival_decoration",
            "stardewcraft.type.scarecrow",
            "stardewcraft.type.carpet",
            "stardewcraft.type.craftable",
            "stardewcraft.type.utility",
            "stardewcraft.type.building",
            "stardewcraft.type.wallpaper"
    );

    private TrashCanService() {
    }

    public static boolean canTrash(ItemStack stack) {
        return InventoryTrashPolicy.canTrash(stack);
    }

    public static TrashQuote quote(ServerPlayer player, ItemStack stack) {
        PlayerStardewData data = PlayerDataManager.getPlayerData(player);
        TrashCanTier tier = TrashCanTier.forLevel(data.getTrashCanLevel());
        if (!canTrash(stack)) {
            return TrashQuote.rejected(tier, stack);
        }

        SellQuote sellQuote = ProfessionSellPriceService.quoteItem(player, stack, SellSource.TRASH_CAN);
        boolean reclaimEligible = isReclaimEligible(stack) && sellQuote.sellable() && sellQuote.finalUnitPrice() > 0;
        int refund = reclaimEligible
                ? calculateRefund(sellQuote.finalUnitPrice(), stack.getCount(), tier.reclaimPercent())
                : 0;
        return new TrashQuote(true, reclaimEligible, sellQuote.finalUnitPrice(), stack.getCount(),
                tier.level(), tier.reclaimPercent(), refund);
    }

    public static TrashResult trashCarried(ServerPlayer player, AbstractContainerMenu menu) {
        ItemStack carried = menu.getCarried();
        TrashQuote quote = quote(player, carried);
        if (!quote.trashable()) {
            return new TrashResult(false, 0, SharedMoneyService.getMoney(player));
        }

        menu.setCarried(ItemStack.EMPTY);
        int paidRefund = creditRefund(player, quote.refund());
        player.getInventory().setChanged();
        menu.broadcastChanges();
        return new TrashResult(true, paidRefund, SharedMoneyService.getMoney(player));
    }

    /** Trashes a server inventory slot used by a screen whose cursor stack is only a client-side view. */
    public static TrashResult trashInventorySlot(ServerPlayer player, int slotIndex, boolean takeOne,
                                                 Item expectedItem, int expectedSourceCount) {
        if (slotIndex < 0 || slotIndex >= player.getInventory().getContainerSize()
                || expectedItem == null || expectedSourceCount <= 0) {
            return new TrashResult(false, 0, SharedMoneyService.getMoney(player));
        }
        ItemStack source = player.getInventory().getItem(slotIndex);
        if (source.isEmpty() || !source.is(expectedItem) || source.getCount() != expectedSourceCount) {
            return new TrashResult(false, 0, SharedMoneyService.getMoney(player));
        }
        ItemStack removed = source.copyWithCount(takeOne ? 1 : source.getCount());
        TrashQuote quote = quote(player, removed);
        if (!quote.trashable()) {
            return new TrashResult(false, 0, SharedMoneyService.getMoney(player));
        }
        source.shrink(removed.getCount());
        if (source.isEmpty()) {
            player.getInventory().setItem(slotIndex, ItemStack.EMPTY);
        }
        int paidRefund = creditRefund(player, quote.refund());
        player.getInventory().setChanged();
        player.inventoryMenu.broadcastChanges();
        return new TrashResult(true, paidRefund, SharedMoneyService.getMoney(player));
    }

    private static int creditRefund(ServerPlayer player, int requestedRefund) {
        int currentMoney = Math.max(0, SharedMoneyService.getMoney(player));
        int paidRefund = Math.min(Math.max(0, requestedRefund), Integer.MAX_VALUE - currentMoney);
        if (paidRefund > 0) {
            SharedMoneyService.addMoney(player, paidRefund);
        }
        return paidRefund;
    }

    public static int calculateRefund(int adjustedUnitSellPrice, int count, int reclaimPercent) {
        if (adjustedUnitSellPrice <= 0 || count <= 0 || reclaimPercent <= 0) {
            return 0;
        }
        long refund = (long) adjustedUnitSellPrice * count * reclaimPercent / 100L;
        return (int) Math.min(Integer.MAX_VALUE, refund);
    }

    public static boolean isReclaimEligible(ItemStack stack) {
        if (!canTrash(stack) || StardewItemDataApi.resolve(stack).isEmpty()) {
            return false;
        }
        Item item = stack.getItem();
        if (item instanceof FishingRodItem || item instanceof PanItem || item instanceof SlingshotItem) {
            return false;
        }
        if (item instanceof StardewCosmeticItem || item instanceof StardewTrinketItem) {
            return false;
        }
        if (item instanceof StardewWeaponItem || item instanceof StardewClubItem || item instanceof StardewDaggerItem
                || item instanceof StardewRingItem || item instanceof CombinedRingItem || item instanceof StardewBootsItem) {
            return true;
        }
        return !NON_RECLAIMABLE_TYPES.contains(StardewItemDataApi.getTypeKey(stack));
    }

    public record TrashQuote(boolean trashable, boolean reclaimEligible, int adjustedUnitSellPrice,
                             int stackCount, int trashCanLevel, int reclaimPercent, int refund) {
        private static TrashQuote rejected(TrashCanTier tier, ItemStack stack) {
            return new TrashQuote(false, false, 0, stack == null ? 0 : stack.getCount(),
                    tier.level(), tier.reclaimPercent(), 0);
        }
    }

    public record TrashResult(boolean success, int refund, int remainingMoney) {
    }
}
