package com.stardew.craft.menu;

/** Shared slot coordinates; chest width never changes the player's nine-column inventory. */
public record ChestMenuLayout(int columns, int storageSlots, int recoverySlots) {
    public static final int NORMAL_CAPACITY = 36;
    public static final ChestMenuLayout NORMAL = new ChestMenuLayout(12, NORMAL_CAPACITY, 0);
    public static final ChestMenuLayout REWARD = new ChestMenuLayout(9, 27, 0);
    public static final ChestMenuLayout STONE_RECOVERY = new ChestMenuLayout(12, NORMAL_CAPACITY, 18);

    public int visibleSlots() { return storageSlots + recoverySlots; }
    public int rows() { return (visibleSlots() + columns - 1) / columns; }
    public int recoveryGap() { return recoverySlots > 0 ? 12 : 0; }
    public int imageWidth() { return columns * 18 + 14; }
    public int imageHeight() { return 114 + rows() * 18 + recoveryGap(); }
    public int playerX() { return 8 + (columns - 9) * 9; }
    public int playerY() { return imageHeight() - 84; }
    public int hotbarY() { return imageHeight() - 26; }
    public int slotX(int index) { return 8 + (index % columns) * 18; }
    public int slotY(int index) {
        return 18 + (index / columns) * 18 + (index >= storageSlots ? recoveryGap() : 0);
    }
}
