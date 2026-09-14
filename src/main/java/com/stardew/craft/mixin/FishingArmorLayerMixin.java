package com.stardew.craft.mixin;

import com.stardew.craft.client.fishing.FishingArmorVisibility;
import com.stardew.craft.client.fishing.FishingPresentationClient;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HumanoidArmorLayer.class)
public class FishingArmorLayerMixin {
    @Unique private boolean fishing$arms;
    @Inject(method="renderArmorPiece(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/EquipmentSlot;ILnet/minecraft/client/model/HumanoidModel;FFFFFF)V",at=@At("HEAD"),cancellable=true)
    private void fishing$armorOwner(PoseStack pose,MultiBufferSource buffers,LivingEntity entity,EquipmentSlot slot,int light,HumanoidModel<?> model,float a,float b,float c,float d,float e,float f,CallbackInfo ci) {
        fishing$arms=entity instanceof AbstractClientPlayer p&&FishingPresentationClient.worldOwned(p);
        if(FishingArmorVisibility.shouldHide(entity,entity.getItemBySlot(slot)))ci.cancel();
    }
    @Inject(method="setPartVisibility",at=@At("RETURN"))
    private void fishing$weightedArmor(HumanoidModel<?> model,EquipmentSlot slot,CallbackInfo ci){if(fishing$arms)model.rightArm.visible=model.leftArm.visible=false;}
}
