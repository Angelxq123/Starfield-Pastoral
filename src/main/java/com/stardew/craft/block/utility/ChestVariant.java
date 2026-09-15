package com.stardew.craft.block.utility;

import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.menu.ChestMenuLayout;
import net.minecraft.world.level.block.Block;

public enum ChestVariant {
    WOOD("wooden_chest", 36, 12, true),
    STONE("stone_chest", 36, 12, true),
    BIG_WOOD("big_chest", 70, 12, true),
    BIG_STONE("big_stone_chest", 70, 12, true),
    JUNIMO("junimo_chest", 9, 9, false);

    public final String id;
    public final int capacity;
    public final boolean dyeable;
    public final ChestMenuLayout layout;
    ChestVariant(String id, int capacity, int columns, boolean dyeable) {
        this.id = id; this.capacity = capacity; this.dyeable = dyeable;
        this.layout = capacity == 36 ? ChestMenuLayout.NORMAL : new ChestMenuLayout(columns, capacity, 0);
    }
    public Block block() {
        return switch (this) {
            case WOOD -> ModBlocks.WOODEN_CHEST.get();
            case STONE -> ModBlocks.STONE_CHEST.get();
            case BIG_WOOD -> ModBlocks.BIG_CHEST.get();
            case BIG_STONE -> ModBlocks.BIG_STONE_CHEST.get();
            case JUNIMO -> ModBlocks.JUNIMO_CHEST.get();
        };
    }
    public static ChestVariant of(Block block) {
        if (block instanceof StorageChestBlock chest) return chest.variant();
        return block instanceof StoneChestBlock ? STONE : WOOD;
    }
}
