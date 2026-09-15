package com.stardew.craft.inventory;

import net.minecraft.world.Container;

/** Storage contract for chest interactions, including addon implementations. */
public interface ChestStorage extends Container {
    int getColorSelection();
    void setColorSelection(int color);
    boolean isInUse();
    default boolean isSharedStorage() { return false; }
}
