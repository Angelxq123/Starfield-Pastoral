package com.stardew.craft.mixin;

import com.stardew.craft.client.weapon.FemurSlamInput;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Minecraft.class)
public abstract class MinecraftFemurChargeInputMixin {
    @Redirect(method = "handleKeybinds", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/KeyMapping;isDown()Z"))
    private boolean stardewcraft$holdSlamWithItsSkillKey(KeyMapping key) {
        return FemurSlamInput.isUseHeld(key);
    }
}
