package com.stardew.craft.mining;

import com.stardew.craft.core.ModMiningDimensions;
import net.minecraft.server.level.ServerLevel;

/** The approved Mine.tmx lobby is permanent architecture at ordinary floor zero. */
public final class MineEntranceBootstrap {
    private MineEntranceBootstrap() {}
    public static void ensureGenerated(ServerLevel level) {
        if (level.dimension() == ModMiningDimensions.STARDEW_MINING) OrdinaryMineRuntime.ensure(level, 0);
    }
}
