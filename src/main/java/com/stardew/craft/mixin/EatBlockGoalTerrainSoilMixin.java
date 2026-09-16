package com.stardew.craft.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.stardew.craft.block.terrain.TerrainGrassBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.EatBlockGoal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Animals can graze authored grass without changing its block or texture variant. */
@Mixin(EatBlockGoal.class)
public abstract class EatBlockGoalTerrainSoilMixin {
    @WrapOperation(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z"))
    private boolean stardewcraft$keepGrass(Level level, BlockPos pos, BlockState result, int flags, Operation<Boolean> original) {
        if (result.is(Blocks.DIRT) && level.getBlockState(pos).getBlock() instanceof TerrainGrassBlock) {
            return false;
        }
        return original.call(level, pos, result, flags);
    }
}
