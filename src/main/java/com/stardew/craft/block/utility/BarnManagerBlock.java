package com.stardew.craft.block.utility;

public final class BarnManagerBlock extends ResidenceManagerBlock {
    public BarnManagerBlock(Properties properties) { super(properties,"stardewcraft:block/barn_manager"); }
    // Legacy menu packets no longer perform building operations; the runtime ledger owns authorization.
    public static boolean tryBuildOrUpgrade(net.minecraft.server.level.ServerLevel level, net.minecraft.core.BlockPos pos, net.minecraft.server.level.ServerPlayer player) { return false; }
    public static boolean tryDemolishBuilding(net.minecraft.server.level.ServerLevel level, net.minecraft.core.BlockPos pos, net.minecraft.server.level.ServerPlayer player) { return false; }
    public static boolean tryRelocateManager(net.minecraft.server.level.ServerLevel level, net.minecraft.core.BlockPos pos, net.minecraft.server.level.ServerPlayer player) { return false; }
}
