package com.stardew.craft.mixin;

import com.stardew.craft.block.decor.GardenPlanterBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SporeBlossomBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Allow the hanging vanilla flower to be planted upright in a soil box. */
@Mixin(SporeBlossomBlock.class)
public abstract class GardenPlanterSporeBlossomMixin {
    @Inject(method="canSurvive",at=@At("HEAD"),cancellable=true)
    private void stardewcraft$planterSupport(BlockState state,LevelReader level,BlockPos pos,CallbackInfoReturnable<Boolean> result){
        if(level.getBlockState(pos.below()).getBlock() instanceof GardenPlanterBlock && !level.isWaterAt(pos))result.setReturnValue(true);
    }
    @Inject(method="updateShape",at=@At("HEAD"),cancellable=true)
    private void stardewcraft$removeUnsupported(BlockState state,Direction direction,BlockState neighbor,LevelAccessor level,BlockPos pos,BlockPos neighborPos,CallbackInfoReturnable<BlockState> result){
        if(direction==Direction.DOWN && !state.canSurvive(level,pos))result.setReturnValue(Blocks.AIR.defaultBlockState());
    }
}
