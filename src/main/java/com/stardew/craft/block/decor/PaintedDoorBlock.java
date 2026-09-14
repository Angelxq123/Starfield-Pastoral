package com.stardew.craft.block.decor;

import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.properties.BlockSetType;

/** Painted doors use vanilla placement, hinges, collision and interaction rules. */
public final class PaintedDoorBlock extends DoorBlock {
    public PaintedDoorBlock(Properties properties) { super(BlockSetType.OAK, properties); }
}
