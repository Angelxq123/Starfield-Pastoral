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
    private final ChestMenuLayout layout;
    private final com.stardew.craft.block.utility.ChestVariant variant;

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
        this(containerId, playerInventory, new SimpleContainer(27), null, null, -1, true, ChestMenuLayout.REWARD, null);
    }

    public static WoodenChestMenu storageClient(int containerId, Inventory inventory) {
        return new WoodenChestMenu(containerId, inventory, new SimpleContainer(36), null, null, -1, true, ChestMenuLayout.NORMAL, com.stardew.craft.block.utility.ChestVariant.WOOD);
    }

    public WoodenChestMenu(int containerId, Inventory playerInventory, Container container, @Nullable WoodenChestBlockEntity chestEntity) {
        this(containerId, playerInventory, container, chestEntity, null, chestEntity != null ? chestEntity.getColorSelection() : 0, false,
                chestEntity != null ? chestEntity.variant().layout : ChestMenuLayout.REWARD,
                chestEntity != null ? chestEntity.variant() : null);
    }

    /** 带通用颜色处理器的构造函数，供 MineChest 等非 WoodenChest 使用 */
    public WoodenChestMenu(int containerId, Inventory playerInventory, Container container,
                           @Nullable IntConsumer colorHandler, int initialColor) {
        this(containerId, playerInventory, container, null, colorHandler, initialColor, false, ChestMenuLayout.REWARD, null);
    }

    public WoodenChestMenu(int containerId, Inventory playerInventory, Container container,
                           @Nullable IntConsumer colorHandler, int initialColor, boolean rewardOnly) {
        this(containerId,playerInventory,container,null,colorHandler,initialColor,rewardOnly,ChestMenuLayout.REWARD,null);
    }

    private WoodenChestMenu(int containerId, Inventory playerInventory, Container container,
                            @Nullable WoodenChestBlockEntity chestEntity,
                            @Nullable IntConsumer colorHandler, int initialColor, boolean rewardOnly, ChestMenuLayout layout, com.stardew.craft.block.utility.ChestVariant variant) {
        super(menuType(variant), containerId);
        this.variant = variant;
        this.layout = layout;
        this.rewardOnly=rewardOnly;
        this.container = container;
        this.chestEntity = chestEntity;
        this.colorHandler = colorHandler;
        this.colorAvailable = (chestEntity != null && variant.dyeable) || colorHandler != null;
        this.woodenPreview = chestEntity != null;
        this.colorSelection = initialColor;

        checkContainerSize(container, layout.visibleSlots());
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

        for (int index = 0; index < layout.visibleSlots(); index++) {
            this.addSlot(new Slot(container, index, layout.slotX(index), layout.slotY(index)) {
                @Override public boolean mayPlace(ItemStack stack) { return !WoodenChestMenu.this.rewardOnly; }
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
    public com.stardew.craft.block.utility.ChestVariant previewVariant() { return variant; }
    public java.util.UUID sharedOwner() {
        return chestEntity instanceof com.stardew.craft.blockentity.StorageChestBlockEntity chest ? chest.sharedOwner() : null;
    }
    public static WoodenChestMenu variantClient(int id, Inventory inventory, com.stardew.craft.block.utility.ChestVariant variant) {
        return new WoodenChestMenu(id, inventory, new SimpleContainer(variant.capacity), null, null, -1, true, variant.layout, variant);
    }
    private static net.minecraft.world.inventory.MenuType<WoodenChestMenu> menuType(com.stardew.craft.block.utility.ChestVariant variant) {
        if (variant == null) return ModMenuTypes.WOODEN_CHEST.get();
        return switch (variant) {
            case BIG_WOOD -> ModMenuTypes.BIG_CHEST.get();
            case BIG_STONE -> ModMenuTypes.BIG_STONE_CHEST.get();
            case JUNIMO -> ModMenuTypes.JUNIMO_CHEST.get();
            default -> ModMenuTypes.WOODEN_STORAGE.get();
        };
    }
    @Override public boolean clickMenuButton(Player player, int id) {
        if (rewardOnly || !stillValid(player)) return false;
        return com.stardew.craft.inventory.ChestMenuActions.handle(this, container, layout.storageSlots(), player, id);
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
        InventoryOrganizeService.organizeContainer(container, layout.visibleSlots());
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
        } else if (!this.moveItemStackTo(stackInSlot, 0, layout.visibleSlots(), false)) {
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
