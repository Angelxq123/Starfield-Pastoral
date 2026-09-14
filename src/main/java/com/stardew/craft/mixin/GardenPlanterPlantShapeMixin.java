package com.stardew.craft.mixin;

import com.stardew.craft.block.decor.GardenPlanterBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Keep selection aligned with the soil-lowered world model, including tall flowers. */
@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class GardenPlanterPlantShapeMixin {
    @Inject(method="getShape(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/phys/shapes/CollisionContext;)Lnet/minecraft/world/phys/shapes/VoxelShape;",at=@At("RETURN"),cancellable=true)
    private void stardewcraft$lowerPlantShape(BlockGetter level,BlockPos pos,CollisionContext context,CallbackInfoReturnable<VoxelShape> result){
        BlockState state=(BlockState)(Object)this;
        if(!GardenPlanterBlock.lowersPlant(level,pos,state))return;
        if(state.is(net.minecraft.world.level.block.Blocks.SPORE_BLOSSOM)) {
            VoxelShape[] shape={net.minecraft.world.phys.shapes.Shapes.empty()};
            result.getReturnValue().forAllBoxes((x1,y1,z1,x2,y2,z2)->shape[0]=net.minecraft.world.phys.shapes.Shapes.or(shape[0],
                    net.minecraft.world.phys.shapes.Shapes.box(x1,.75-y2,1-z2,x2,.75-y1,1-z1)));
            result.setReturnValue(shape[0]);
        } else result.setReturnValue(result.getReturnValue().move(0,-.25,0));
    }
}
