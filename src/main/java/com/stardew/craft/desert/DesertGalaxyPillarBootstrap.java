package com.stardew.craft.desert;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.decor.MapDecorStaticBlock;
import com.stardew.craft.core.ModDimensions;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public final class DesertGalaxyPillarBootstrap {

    public static final BlockPos NORTH_PILLAR_POS = new BlockPos(-201, 64, -200);
    public static final BlockPos EAST_PILLAR_POS = new BlockPos(-198, 64, -205);
    public static final BlockPos WEST_PILLAR_POS = new BlockPos(-195, 64, -200);
    public static final BlockPos RITUAL_TRIGGER_POS = new BlockPos(-198, 64, -202);

    private static final int CHECK_INTERVAL_TICKS = 20;
    private static int tickCounter;

    private DesertGalaxyPillarBootstrap() {}

    public static void ensurePlaced(ServerLevel level, String reason) {
        if (!ModDimensions.STARDEW_VALLEY.equals(level.dimension())) {
            return;
        }
        if (level.getChunkSource().getChunkNow(
                RITUAL_TRIGGER_POS.getX() >> 4,
                RITUAL_TRIGGER_POS.getZ() >> 4) == null) {
            return;
        }

        if (pillarsPresent(level)) {
            return;
        }
        if (DesertMapBootstrap.isPlacementInProgress()) {
            return;
        }

        placePillar(level, NORTH_PILLAR_POS, Direction.SOUTH);
        placePillar(level, EAST_PILLAR_POS, Direction.WEST);
        placePillar(level, WEST_PILLAR_POS, Direction.EAST);

        StardewCraft.LOGGER.info("[DESERT] Galaxy pillars placed. reason={}, trigger={}", reason, RITUAL_TRIGGER_POS);
    }

    public static void tick(ServerLevel level) {
        tickCounter++;
        if (tickCounter < CHECK_INTERVAL_TICKS) {
            return;
        }
        tickCounter = 0;
        ensurePlaced(level, "level_tick");
    }

    private static void placePillar(ServerLevel level, BlockPos pos, Direction facing) {
        Block block = ModBlocks.GALAXY_PILLAR.get();
        BlockState state = block.defaultBlockState()
                .setValue(MapDecorStaticBlock.PART, MapDecorStaticBlock.Part.MAIN)
                .setValue(MapDecorStaticBlock.FACING, facing);

        level.setBlock(pos, state, 3);
        block.setPlacedBy(level, pos, state, null, ItemStack.EMPTY);
    }

    private static boolean pillarsPresent(ServerLevel level) {
        return pillarPresent(level, NORTH_PILLAR_POS, Direction.SOUTH)
                && pillarPresent(level, EAST_PILLAR_POS, Direction.WEST)
                && pillarPresent(level, WEST_PILLAR_POS, Direction.EAST);
    }

    private static boolean pillarPresent(ServerLevel level, BlockPos mainPos, Direction facing) {
        Block block = ModBlocks.GALAXY_PILLAR.get();
        return isPillarPart(level, mainPos, block, MapDecorStaticBlock.Part.MAIN, facing)
                && isPillarPart(level, mainPos.above(), block, MapDecorStaticBlock.Part.EXTENSION, facing)
                && isPillarPart(level, mainPos.above(2), block, MapDecorStaticBlock.Part.EXTENSION, facing);
    }

    private static boolean isPillarPart(ServerLevel level, BlockPos pos, Block block, MapDecorStaticBlock.Part part, Direction facing) {
        BlockState state = level.getBlockState(pos);
        return state.is(block)
                && state.hasProperty(MapDecorStaticBlock.PART)
                && state.getValue(MapDecorStaticBlock.PART) == part
                && state.hasProperty(MapDecorStaticBlock.FACING)
                && state.getValue(MapDecorStaticBlock.FACING) == facing;
    }
}
