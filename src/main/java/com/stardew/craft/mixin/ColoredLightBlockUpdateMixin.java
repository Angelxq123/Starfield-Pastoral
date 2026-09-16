package com.stardew.craft.mixin;
import com.stardew.craft.client.light.ColoredLightEngine;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ClientLevel.class)
public abstract class ColoredLightBlockUpdateMixin {
    @Inject(method="sendBlockUpdated",at=@At("RETURN"))
    private void stardewcraft$invalidateLight(BlockPos pos,BlockState oldState,BlockState newState,int flags,CallbackInfo ci) {
        if (oldState != newState) ColoredLightEngine.blockChanged(pos);
    }
}
