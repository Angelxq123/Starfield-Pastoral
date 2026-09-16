package com.stardew.craft.specialorder;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public enum SpecialOrderDropBoxAnchor {
    WILLY_BARREL("WillyBarrel", "stardewcraft.special_orders.dropbox.willy_barrel", box(73, 60, 151)),
    PAM_KITCHEN("PamKitchen", "stardewcraft.special_orders.dropbox.pam_kitchen", box(72, 35, 1)),
    PIERRE_BOX("PierreBox", "stardewcraft.special_orders.dropbox.pierre_box", box(37, 36, -9, 38, 36, -9)),
    ROBIN_WOOD("RobinWood", "stardewcraft.special_orders.dropbox.robin_wood", box(25, 51, -123)),
    GUS_FRIDGE("GusFridge", "stardewcraft.special_orders.dropbox.gus_fridge", box(31, 36, 8, 31, 37, 8)),
    RAILROAD_DUMPSTER("Dumpster", "stardewcraft.special_orders.dropbox.railroad_dumpster", box(27, 86, -209, 28, 86, -208)),
    EVELYN_KITCHEN("EvelynKitchen", "stardewcraft.special_orders.dropbox.evelyn_kitchen", box(41, 22, -4, 41, 23, -4)),
    GUNTHER("GuntherBox", "stardewcraft.special_orders.dropbox.gunther", box(108, 38, 35));

    private static final List<SpecialOrderDropBoxAnchor> VALUES = Arrays.asList(values());

    private final String dropBoxId;
    private final String translationKey;
    private final BlockPos min;
    private final BlockPos max;
    private final AABB bounds;

    SpecialOrderDropBoxAnchor(String dropBoxId, String translationKey, Bounds bounds) {
        this.dropBoxId = dropBoxId;
        this.translationKey = translationKey;
        this.min = bounds.min;
        this.max = bounds.max;
        this.bounds = new AABB(min.getX(), min.getY(), min.getZ(), max.getX() + 1.0D, max.getY() + 1.0D, max.getZ() + 1.0D);
    }

    public String dropBoxId() {
        return dropBoxId;
    }

    public String translationKey() {
        return translationKey;
    }

    public BlockPos min() {
        return min;
    }

    public BlockPos max() {
        return max;
    }

    public AABB bounds() {
        return bounds;
    }

    public boolean contains(BlockPos pos) {
        return pos.getX() >= min.getX() && pos.getX() <= max.getX()
            && pos.getY() >= min.getY() && pos.getY() <= max.getY()
            && pos.getZ() >= min.getZ() && pos.getZ() <= max.getZ();
    }

    public static List<SpecialOrderDropBoxAnchor> all() {
        return VALUES;
    }

    public static Optional<SpecialOrderDropBoxAnchor> byId(String dropBoxId) {
        return VALUES.stream().filter(anchor -> anchor.dropBoxId.equals(dropBoxId)).findFirst();
    }

    public static Optional<SpecialOrderDropBoxAnchor> at(BlockPos pos) {
        return VALUES.stream().filter(anchor -> anchor.contains(pos)).findFirst();
    }

    private static Bounds box(int x, int y, int z) {
        return box(x, y, z, x, y, z);
    }

    private static Bounds box(int x1, int y1, int z1, int x2, int y2, int z2) {
        return new Bounds(
            new BlockPos(Math.min(x1, x2), Math.min(y1, y2), Math.min(z1, z2)),
            new BlockPos(Math.max(x1, x2), Math.max(y1, y2), Math.max(z1, z2))
        );
    }

    private record Bounds(BlockPos min, BlockPos max) {
    }
}
