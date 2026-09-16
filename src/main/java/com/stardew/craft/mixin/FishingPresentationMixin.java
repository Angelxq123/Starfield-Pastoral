package com.stardew.craft.mixin;

import com.stardew.craft.client.fishing.FishingPresentationClient;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.world.entity.LivingEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandLayer.class)
public class FishingPresentationMixin {
    @Inject(method="render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V",at=@At("HEAD"),cancellable=true)
    private void fishing$replaceHeldRod(PoseStack pose,MultiBufferSource buffers,int light,LivingEntity entity,float a,float b,float c,float d,float e,float f,CallbackInfo ci){
        if(entity instanceof AbstractClientPlayer player&&FishingPresentationClient.worldOwned(player))ci.cancel();
    }
}
