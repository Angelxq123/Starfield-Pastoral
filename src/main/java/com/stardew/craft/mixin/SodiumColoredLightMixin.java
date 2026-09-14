package com.stardew.craft.mixin;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.stardew.craft.client.light.ColoredLightEngine;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
@Pseudo
@Mixin(targets="net.caffeinemc.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderer",remap=false)
public abstract class SodiumColoredLightMixin {
    @WrapOperation(method="bufferQuad",at=@At(value="INVOKE",target="Lnet/caffeinemc/mods/sodium/client/render/frapi/mesh/MutableQuadViewImpl;color(I)I"))
    private int stardewcraft$rgb(@Coerce Object quad,int vertex,Operation<Integer> original) {
        int color=original.call(quad,vertex);
        if(!ColoredLightEngine.active()) return color;
        BlockPos pos=((SodiumColoredLightPositionAccessor)this).stardewcraft$getLightPosition();
        var q=(SodiumColoredLightQuadAccessor)quad;var face=q.stardewcraft$getLightFace();
        if(!q.stardewcraft$hasShade()) return color;
        int tint=ColoredLightEngine.tint(pos.getX()+q.stardewcraft$getX(vertex)+face.getStepX()*.02,
                pos.getY()+q.stardewcraft$getY(vertex)+face.getStepY()*.02,pos.getZ()+q.stardewcraft$getZ(vertex)+face.getStepZ()*.02,q.stardewcraft$getLight(vertex));
        return ColoredLightEngine.multiplyArgb(color,tint);
    }
    @WrapOperation(method="bufferQuad",at=@At(value="INVOKE",target="Lnet/caffeinemc/mods/sodium/client/render/frapi/mesh/MutableQuadViewImpl;lightmap(I)I"))
    private int stardewcraft$movingLight(@Coerce Object quad,int vertex,Operation<Integer> original) {
        int light=original.call(quad,vertex);if(!ColoredLightEngine.active())return light;
        BlockPos pos=((SodiumColoredLightPositionAccessor)this).stardewcraft$getLightPosition();
        var q=(SodiumColoredLightQuadAccessor)quad;var face=q.stardewcraft$getLightFace();
        return ColoredLightEngine.light(pos.getX()+q.stardewcraft$getX(vertex)+face.getStepX()*.02,
                pos.getY()+q.stardewcraft$getY(vertex)+face.getStepY()*.02,pos.getZ()+q.stardewcraft$getZ(vertex)+face.getStepZ()*.02,light);
    }

}
