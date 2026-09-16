package com.stardew.craft.mixin;

import com.stardew.craft.client.fishing.FishingPresentationClient;
import com.stardew.craft.client.fishing.FishingPlayerPose;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerModel.class)
public class FishingPlayerModelMixin {
    @Unique private boolean fishing$posed;
    @Inject(method="setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V",at=@At("HEAD"))
    private void fishing$restoreBody(LivingEntity entity,float a,float b,float c,float d,float e,CallbackInfo ci) {
        if(!fishing$posed)return;
        var model=(PlayerModel<?>)(Object)this;
        model.body.setPos(0,0,0);model.body.zRot=0;model.head.setPos(0,0,0);
        fishing$posed=false;
    }

    @Inject(method="setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V",at=@At("RETURN"))
    private void fishing$continuousArms(LivingEntity entity,float a,float b,float c,float d,float e,CallbackInfo ci) {
        if(!(entity instanceof AbstractClientPlayer player)||!FishingPresentationClient.worldOwned(player))return;
        var state=FishingPresentationClient.state(player);if(state==null)return;state.sample();
        FishingPlayerPose.apply((PlayerModel<?>)(Object)this,state.pose,player.getMainArm()==net.minecraft.world.entity.HumanoidArm.LEFT);
        fishing$posed=true;
    }
}
