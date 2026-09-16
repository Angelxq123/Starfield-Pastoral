package com.stardew.craft.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.stardew.craft.client.fishing.FishingBiteVisuals;
import com.stardew.craft.client.fishing.FishingPresentationClient;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.FishingHookRenderer;
import net.minecraft.world.entity.projectile.FishingHook;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import org.spongepowered.asm.mixin.Mixin;

/** One owner for native suppression and the vanilla fallback's scoped bite transform. */
@Mixin(FishingHookRenderer.class)
public class FishingHookPresentationMixin {
    @WrapMethod(method="render(Lnet/minecraft/world/entity/projectile/FishingHook;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V")
    private void fishing$oneTackle(FishingHook hook,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int light,Operation<Void> original) {
        boolean nativeTackle=hook.getOwner() instanceof AbstractClientPlayer p&&FishingPresentationClient.eligible(p);
        FishingBiteVisuals.renderHook(pose,nativeTackle,FishingBiteVisuals.getBobberDipOffsetY(hook.getId()),
                ()->original.call(hook,yaw,partial,pose,buffers,light));
    }
}
