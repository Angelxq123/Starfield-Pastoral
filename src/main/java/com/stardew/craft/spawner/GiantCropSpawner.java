package com.stardew.craft.spawner;

import com.stardew.craft.api.v1.internal.giant.GiantCropGrowth;
import com.stardew.craft.block.crop.StardewCropBlock;
import com.stardew.craft.farm.OfflineFarmCatchUp;
import com.stardew.craft.manager.CropGrowthManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import java.util.Set;

/** Administrative growth bridge. Normal and offline days use CropGrowthManager.settleCrops. */
public final class GiantCropSpawner {
    public static final double SPAWN_CHANCE = .01;
    private GiantCropSpawner() {}

    /** Trigger is the northwest root, matching normal settlement. Repeated calls cannot reroll a day. */
    public static void tryRoll(ServerLevel level, BlockPos anchor, StardewCropBlock cropBlock) {
        var positions = CropGrowthManager.get(level).getAllCropPositions().stream()
                .filter(p -> p.dimension().equals(level.dimension())).map(p -> p.pos()).toList();
        GiantCropGrowth.process(level, positions, Set.of(anchor), OfflineFarmCatchUp.computeAbsoluteDay(), false, Set.of(anchor));
    }
}
