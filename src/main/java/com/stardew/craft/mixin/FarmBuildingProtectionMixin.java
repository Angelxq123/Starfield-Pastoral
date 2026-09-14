package com.stardew.craft.mixin;

import com.stardew.craft.building.runtime.BuildingProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Level.class)
public abstract class FarmBuildingProtectionMixin {
    @Inject(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z", at = @At("HEAD"), cancellable = true)
    private void stardewcraft$protectPrefabReplacement(BlockPos pos, BlockState state, int flags, int recursion,
                                                       CallbackInfoReturnable<Boolean> result) {
        if ((Object) this instanceof ServerLevel level && BuildingProtection.deniesReplacement(level, pos, state)) result.setReturnValue(false);
    }
    @Inject(method = "destroyBlock(Lnet/minecraft/core/BlockPos;ZLnet/minecraft/world/entity/Entity;I)Z", at = @At("HEAD"), cancellable = true)
    private void stardewcraft$protectPrefabDrops(BlockPos pos, boolean drops, Entity entity, int recursion,
                                                 CallbackInfoReturnable<Boolean> result) {
        if ((Object) this instanceof ServerLevel level && BuildingProtection.protects(level, pos)) result.setReturnValue(false);
    }
}
