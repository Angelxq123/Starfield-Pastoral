package com.stardew.craft.mixin;

import com.stardew.craft.client.fishing.FishingMinigameHud;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.KeyboardInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardInput.class)
public class FishingKeyboardInputMixin {
    @Inject(method="tick",at=@At("RETURN"))
    private void fishing$consumeJump(boolean slow,float factor,CallbackInfo ci){if(FishingMinigameHud.active())((Input)(Object)this).jumping=false;}
}
