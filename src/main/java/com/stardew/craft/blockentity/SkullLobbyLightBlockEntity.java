package com.stardew.craft.blockentity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Client light discovery for static native shrine and brazier parts; no tick or inventory. */
public final class SkullLobbyLightBlockEntity extends BlockEntity {
    public SkullLobbyLightBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SKULL_LOBBY_LIGHT.get(), pos, state);
    }
}
