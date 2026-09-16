package com.stardew.craft.floor;

import com.stardew.craft.item.ModItems;
import net.minecraft.world.item.Item;

/** Stable save/network IDs. Append new materials; never reorder existing entries. */
public enum SurfaceFloorType {
    WOOD("wood_floor", 4, 4, 47),
    STONE("stone_floor", 4, 4, 47),
    WEATHERED("weathered_floor", 4, 4, 47),
    CRYSTAL("crystal_floor", 4, 3, 47),
    STRAW("straw_floor", 2, 2, 16),
    GRAVEL_PATH("gravel_path", 2, 2, 16),
    WOOD_PATH("wood_path", 2, 2, 16),
    CRYSTAL_PATH("crystal_path", 2, 2, 16),
    COBBLESTONE_PATH("cobblestone_path", 2, 2, 16),
    STEPPING_STONE_PATH("stepping_stone_path", 1, 1, 1),
    BRICK("brick_floor", 3, 3, 47),
    RUSTIC_PLANK("rustic_plank_floor", 4, 4, 16),
    STONE_WALKWAY("stone_walkway_floor", 2, 2, 47);

    public final String id;
    public final int phaseX, phaseZ, masks;

    SurfaceFloorType(String id, int phaseX, int phaseZ, int masks) {
        this.id = id;
        this.phaseX = phaseX;
        this.phaseZ = phaseZ;
        this.masks = masks;
    }

    public Item item() { return ModItems.SURFACE_FLOORS.get(id).get(); }

    public static SurfaceFloorType byId(String id) {
        for (var type : values()) if (type.id.equals(id)) return type;
        return null;
    }

    public static SurfaceFloorType byNetworkId(int id) {
        return id >= 0 && id < values().length ? values()[id] : null;
    }
}
