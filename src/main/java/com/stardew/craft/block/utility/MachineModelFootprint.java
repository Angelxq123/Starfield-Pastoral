package com.stardew.craft.block.utility;

import com.stardew.craft.block.decor.MapDecorStaticBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** One-time footprint repair when loading the resized B008 machines from older saves. */
public final class MachineModelFootprint {
    private MachineModelFootprint() {}

    public static boolean repair(Level level, BlockPos pos, BlockState state) {
        if (!(state.getBlock() instanceof MapUtilityStaticBlock block)) return true;
        // Adjacent owners must be loaded before an extension can be judged orphaned.
        if (!level.hasChunksAt(pos.offset(-3, -1, -3), pos.offset(3, 3, 3))) return false;
        if (block instanceof KegBlock) {
            // Old kegs occupied the cell above their 18.5-unit body; the approved body is 16 high.
            for (BlockPos cell : BlockPos.betweenClosed(pos.offset(-1, 1, -1), pos.offset(1, 1, 1))) {
                var existing = level.getBlockState(cell);
                if (existing.is(block) && existing.getValue(MapDecorStaticBlock.PART) == MapDecorStaticBlock.Part.EXTENSION
                        && block.findMainPos(level, cell, existing) == null) {
                    MapDecorStaticBlock.runWithDropsSuppressed(() -> level.removeBlock(cell, false));
                }
            }
        }
        // Placement validates the entire footprint first. Never replace another saved block.
        block.placeExtensions(level, pos, state);
        return true;
    }
}
