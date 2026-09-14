package com.stardew.craft.menu;

import com.stardew.craft.block.utility.WoodenChestColorPalette;
import com.stardew.craft.blockentity.WoodenChestBlockEntity;
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
import java.util.function.IntConsumer;

@SuppressWarnings("null")
public class WoodenChestMenu extends AbstractContainerMenu {
    private static final int ROWS = 3;
    private static final int COLS = 9;
    private static final int CHEST_SIZE = ROWS * COLS;

    private final Container container;
    @Nullable
    private final WoodenChestBlockEntity chestEntity;
    /** 通用颜色变更处理器，供非 WoodenChestBlockEntity 的箱子使用 */
    @Nullable
    private final IntConsumer colorHandler;
    private int colorSelection;
    private boolean colorAvailable;
    private boolean woodenPreview;

    public WoodenChestMenu(int containerId, Inventory playerInventory) {
        // Client-side placeholder: wait for the server's capabilities before enabling actions.
        this(containerId, playerInventory, new SimpleContainer(CHEST_SIZE), null, null, -1, true);
    }

    public WoodenChestMenu(int containerId, Inventory playerInventory, Container container, @Nullable WoodenChestBlockEntity chestEntity) {
        this(containerId, playerInventory, container, chestEntity, null, chestEntity != null ? chestEntity.getColorSelection() : 0, false);
    }

    /** 带通用颜色处理器的构造函数，供 MineChest 等非 WoodenChest 使用 */
    public WoodenChestMenu(int containerId, Inventory playerInventory, Container container,
                           @Nullable IntConsumer colorHandler, int initialColor) {
        this(containerId, playerInventory, container, null, colorHandler, initialColor, false);
    }

    public WoodenChestMenu(int containerId, Inventory playerInventory, Container container,
                           @Nullable IntConsumer colorHandler, int initialColor, boolean rewardOnly) {
        this(containerId,playerInventory,container,null,colorHandler,initialColor,rewardOnly);
    }

    private WoodenChestMenu(int containerId, Inventory playerInventory, Container container,
                            @Nullable WoodenChestBlockEntity chestEntity,
                            @Nullable IntConsumer colorHandler, int initialColor, boolean rewardOnly) {
        super(ModMenuTypes.WOODEN_CHEST.get(), containerId);
        this.rewardOnly=rewardOnly;
        this.container = container;
        this.chestEntity = chestEntity;
        this.colorHandler = colorHandler;
        this.colorAvailable = chestEntity != null || colorHandler != null;
        this.woodenPreview = chestEntity != null;
        this.colorSelection = initialColor;

        checkContainerSize(container, CHEST_SIZE);
        container.startOpen(playerInventory.player);

        this.addDataSlot(new DataSlot() {
            @Override
            public int get() {
                return WoodenChestMenu.this.chestEntity != null ? WoodenChestMenu.this.chestEntity.getColorSelection() : WoodenChestMenu.this.colorSelection;
            }

            @Override
            public void set(int value) {
                WoodenChestMenu.this.colorSelection = WoodenChestColorPalette.clampIndex(value);
            }
        });

        this.addDataSlot(new DataSlot() {
            @Override
            public int get() {
                return (WoodenChestMenu.this.rewardOnly ? 1 : 0) | (WoodenChestMenu.this.colorAvailable ? 2 : 0)
                        | (WoodenChestMenu.this.woodenPreview ? 4 : 0);
            }

            @Override
            public void set(int value) {
                WoodenChestMenu.this.rewardOnly = (value & 1) != 0;
                WoodenChestMenu.this.colorAvailable = (value & 2) != 0;
                WoodenChestMenu.this.woodenPreview = (value & 4) != 0;
            }
        });

        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                this.addSlot(new Slot(container, col + row * COLS, 8 + col * 18, 18 + row * 18) {
                    @Override public boolean mayPlace(ItemStack stack) { return !WoodenChestMenu.this.rewardOnly; }
                });
            }
        }

        int playerInvY = 84;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < COLS; col++) {
                this.addSlot(new Slot(playerInventory, col + row * COLS + COLS, 8 + col * 18, playerInvY + row * 18));
            }
        }

        int hotbarY = 142;
        for (int col = 0; col < COLS; col++) {
            this.addSlot(new Slot(playerInventory, col, 8 + col * 18, hotbarY));
        }
    }

    private boolean rewardOnly;

    public boolean canChangeColor() {
        return !rewardOnly && colorAvailable;
    }

    public boolean canOrganize() {
        return !rewardOnly;
    }

    public boolean hasWoodenPreview() { return woodenPreview; }

    public int getColorSelection() {
        return colorSelection;
    }

    public void setClientPreviewColorSelection(int selection) {
        if (!canChangeColor()) return;
        colorSelection = WoodenChestColorPalette.clampIndex(selection);
    }

    public void setColorSelectionFromClient(int selection) {
        if (!canChangeColor()) return;
        if (chestEntity != null) {
            chestEntity.setColorSelection(selection);
        } else if (colorHandler != null) {
            colorHandler.accept(selection);
        }
    }

    public void organizeContainer() {
        if (rewardOnly) return;
        InventoryOrganizeService.organizeContainer(container, CHEST_SIZE);
        for (int i = 0; i < CHEST_SIZE; i++) {
            this.slots.get(i).setChanged();
        }
        this.broadcastChanges();
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }

        ItemStack stackInSlot = slot.getItem();
        result = stackInSlot.copy();

        if (index < CHEST_SIZE) {
            if (!this.moveItemStackTo(stackInSlot, CHEST_SIZE, this.slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!this.moveItemStackTo(stackInSlot, 0, CHEST_SIZE, false)) {
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
