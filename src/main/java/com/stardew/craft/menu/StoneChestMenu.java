package com.stardew.craft.menu;

import com.stardew.craft.block.utility.WoodenChestColorPalette;
import com.stardew.craft.blockentity.StoneChestBlockEntity;
import com.stardew.craft.inventory.InventoryOrganizeService;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;

@SuppressWarnings("null")
public class StoneChestMenu extends AbstractContainerMenu {
    private final ChestMenuLayout layout;

    private final Container container;
    @Nullable
    private final StoneChestBlockEntity chestEntity;
    private int colorSelection;

    public StoneChestMenu(int containerId, Inventory playerInventory) {
        this(containerId, playerInventory, new SimpleContainer(ChestMenuLayout.NORMAL_CAPACITY), null);
    }

    public static StoneChestMenu recoveryClient(int containerId, Inventory inventory) {
        return new StoneChestMenu(containerId, inventory, new SimpleContainer(54), null);
    }

    public StoneChestMenu(int containerId, Inventory playerInventory, Container container, @Nullable StoneChestBlockEntity chestEntity) {
        super(container.getContainerSize() > ChestMenuLayout.NORMAL_CAPACITY
                ? ModMenuTypes.STONE_CHEST_RECOVERY.get() : ModMenuTypes.STONE_CHEST.get(), containerId);
        this.layout = container.getContainerSize() > ChestMenuLayout.NORMAL_CAPACITY
                ? ChestMenuLayout.STONE_RECOVERY : ChestMenuLayout.NORMAL;
        this.container = container;
        this.chestEntity = chestEntity;
        this.colorSelection = chestEntity != null ? chestEntity.getColorSelection() : -1;

        checkContainerSize(container, layout.visibleSlots());
        container.startOpen(playerInventory.player);

        this.addDataSlot(new DataSlot() {
            @Override
            public int get() {
                return StoneChestMenu.this.chestEntity != null ? StoneChestMenu.this.chestEntity.getColorSelection() : StoneChestMenu.this.colorSelection;
            }

            @Override
            public void set(int value) {
                StoneChestMenu.this.colorSelection = WoodenChestColorPalette.clampIndex(value);
            }
        });

        for (int index = 0; index < layout.visibleSlots(); index++) {
            final boolean recovery = index >= layout.storageSlots();
            this.addSlot(new Slot(container, index, layout.slotX(index), layout.slotY(index)) {
                @Override public boolean mayPlace(ItemStack stack) { return !recovery; }
            });
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, layout.playerX() + col * 18, layout.playerY() + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInventory, col, layout.playerX() + col * 18, layout.hotbarY()));
        }
    }

    public ChestMenuLayout layout() { return layout; }
    @Override public boolean clickMenuButton(Player player, int id) {
        if (!stillValid(player)) return false;
        return com.stardew.craft.inventory.ChestMenuActions.handle(this, container, layout.storageSlots(), player, id);
    }


    public int getColorSelection() {
        return colorSelection;
    }

    public void setClientPreviewColorSelection(int selection) {
        colorSelection = WoodenChestColorPalette.clampIndex(selection);
    }

    public void setColorSelectionFromClient(int selection) {
        if (chestEntity == null) {
            return;
        }
        chestEntity.setColorSelection(selection);
    }

    public void organizeContainer() {
        InventoryOrganizeService.organizeContainer(container, layout.storageSlots());
        for (int i = 0; i < layout.visibleSlots(); i++) {
            this.slots.get(i).setChanged();
        }
        this.broadcastChanges();
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        ItemStack result = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }

        ItemStack stackInSlot = slot.getItem();
        result = stackInSlot.copy();

        if (index < layout.visibleSlots()) {
            if (!this.moveItemStackTo(stackInSlot, layout.visibleSlots(), this.slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!this.moveItemStackTo(stackInSlot, 0, layout.storageSlots(), false)) {
            return ItemStack.EMPTY;
        }

        if (stackInSlot.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }

        return result;
    }

    @Override
    public boolean stillValid(Player player) {
        return this.container.stillValid(player);
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        this.container.stopOpen(player);
    }
}
