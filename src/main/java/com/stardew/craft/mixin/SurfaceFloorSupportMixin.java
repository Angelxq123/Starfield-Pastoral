package com.stardew.craft.mixin;

import com.stardew.craft.floor.SurfaceFloorData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelChunk.class)
public abstract class SurfaceFloorSupportMixin {
    @Shadow @Final Level level;

    @Inject(method = "setBlockState", at = @At("RETURN"))
    private void stardewcraft$floorSupport(BlockPos pos, BlockState state, boolean moving,
                                          CallbackInfoReturnable<BlockState> callback) {
        BlockState old = callback.getReturnValue();
        if (old != null && !old.isAir() && old != state && level instanceof ServerLevel server) {
            SurfaceFloorData.supportChanged(server, pos);
        }
    }
}
