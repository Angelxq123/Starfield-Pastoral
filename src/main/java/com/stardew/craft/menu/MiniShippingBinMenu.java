package com.stardew.craft.menu;

import com.stardew.craft.blockentity.ShippingBinBlockEntity;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Nine recoverable slots; only shippable items may be deposited. */
public final class MiniShippingBinMenu extends ChestMenu {
    public MiniShippingBinMenu(int id, Inventory inventory) {
        this(id, inventory, new SimpleContainer(9));
    }
    public MiniShippingBinMenu(int id, Inventory inventory, Container container) {
        super(ModMenuTypes.MINI_SHIPPING_BIN.get(), id, inventory, container, 1);
        for (int i = 0; i < 9; i++) {
            Slot original = slots.get(i);
            Slot slot = new Slot(container, i, original.x, original.y) {
                @Override public boolean mayPlace(ItemStack stack) { return ShippingBinBlockEntity.canShip(stack); }
            };
            slot.index = original.index;
            slots.set(i, slot);
        }
    }
}
