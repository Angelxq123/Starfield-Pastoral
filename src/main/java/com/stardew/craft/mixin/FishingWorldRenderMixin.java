package com.stardew.craft.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.stardew.craft.client.fishing.FishingWorldRenderScope;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(LevelRenderer.class)
public class FishingWorldRenderMixin {
    @WrapMethod(method="renderEntity(Lnet/minecraft/world/entity/Entity;DDDFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;)V")
    private void fishing$worldActor(Entity entity,double x,double y,double z,float partial,PoseStack pose,MultiBufferSource buffers,Operation<Void> original) {
        FishingWorldRenderScope.render(entity.getUUID(),()->original.call(entity,x,y,z,partial,pose,buffers));
    }
}
