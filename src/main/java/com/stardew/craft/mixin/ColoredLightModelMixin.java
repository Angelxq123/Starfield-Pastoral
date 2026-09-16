package com.stardew.craft.mixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.stardew.craft.client.light.ColoredLightEngine;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
@Mixin(ModelBlockRenderer.class)
public abstract class ColoredLightModelMixin {
    @WrapMethod(method="putQuadData")
    private void stardewcraft$rgb(BlockAndTintGetter level,BlockState state,BlockPos pos,VertexConsumer consumer,PoseStack.Pose pose,
            BakedQuad quad,float b0,float b1,float b2,float b3,int l0,int l1,int l2,int l3,int overlay,Operation<Void> original) {
        if (ColoredLightEngine.active() && quad.isShade()) {
            int[] data=quad.getVertices().clone();int stride=data.length/4;int[] lights={l0,l1,l2,l3};Direction face=quad.getDirection();
            for(int i=0;i<4;i++) {
                int o=i*stride;
                double x=pos.getX()+Float.intBitsToFloat(data[o])+face.getStepX()*.02,
                        y=pos.getY()+Float.intBitsToFloat(data[o+1])+face.getStepY()*.02,z=pos.getZ()+Float.intBitsToFloat(data[o+2])+face.getStepZ()*.02;
                lights[i]=ColoredLightEngine.light(x,y,z,lights[i]);
                int tint=ColoredLightEngine.tint(x,y,z,lights[i]);
                int abgr=data[o+3];int argb=(abgr&0xff00ff00)|(abgr&255)<<16|(abgr>>>16&255);
                int rgb=ColoredLightEngine.multiplyArgb(argb,tint);data[o+3]=(rgb&0xff00ff00)|(rgb&255)<<16|(rgb>>>16&255);
            }
            l0=lights[0];l1=lights[1];l2=lights[2];l3=lights[3];
            quad=new BakedQuad(data,quad.getTintIndex(),quad.getDirection(),quad.getSprite(),quad.isShade(),quad.hasAmbientOcclusion());
        }
        original.call(level,state,pos,consumer,pose,quad,b0,b1,b2,b3,l0,l1,l2,l3,overlay);
    }
}
