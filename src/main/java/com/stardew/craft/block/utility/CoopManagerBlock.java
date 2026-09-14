package com.stardew.craft.block.utility;

public final class CoopManagerBlock extends ResidenceManagerBlock {
    public static final String TAG_RELOCATE = "stardewcraft_manager_relocate";
    public static final String TAG_BUILDING_ID = "buildingId";
    public static final String TAG_OWNER = "owner";
    public static final String TAG_DIMENSION = "dimension";
    public static final String TAG_FAMILY = "family";
    public static final String TAG_TIER = "tier";
    public static final String TAG_ANIMAL_COUNT = "animalCount";
    public static final String TAG_STRUCTURE_REVISION = "structureRevision";
    public CoopManagerBlock(Properties properties) { super(properties,"stardewcraft:block/coop_manager"); }
    // Legacy menu packets no longer perform building operations; the runtime ledger owns authorization.
    public static boolean tryBuildOrUpgrade(net.minecraft.server.level.ServerLevel level, net.minecraft.core.BlockPos pos, net.minecraft.server.level.ServerPlayer player) { return false; }
    public static boolean tryDemolishBuilding(net.minecraft.server.level.ServerLevel level, net.minecraft.core.BlockPos pos, net.minecraft.server.level.ServerPlayer player) { return false; }
    public static boolean tryRelocateManager(net.minecraft.server.level.ServerLevel level, net.minecraft.core.BlockPos pos, net.minecraft.server.level.ServerPlayer player) { return false; }
}
