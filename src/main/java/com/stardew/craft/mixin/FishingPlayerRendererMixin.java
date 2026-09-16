package com.stardew.craft.mixin;

import com.stardew.craft.client.fishing.FishingPresentationClient;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import org.spongepowered.asm.mixin.Mixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

@Mixin(PlayerRenderer.class)
public class FishingPlayerRendererMixin {
    @WrapMethod(method="render(Lnet/minecraft/client/player/AbstractClientPlayer;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V")
    private void fishing$faceTheCast(AbstractClientPlayer player,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int light,Operation<Void> original){
        if(!FishingPresentationClient.worldOwned(player)){original.call(player,yaw,partial,pose,buffers,light);return;}
        float body=player.yBodyRot,oldBody=player.yBodyRotO;
        player.yBodyRot=player.getYRot();player.yBodyRotO=player.yRotO;
        try {original.call(player,yaw,partial,pose,buffers,light);}
        finally {player.yBodyRot=body;player.yBodyRotO=oldBody;}
    }
}
