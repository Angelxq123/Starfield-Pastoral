package com.stardew.craft.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Invoker;

@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.model.quad.ModelQuadView", remap = false)
public interface SodiumRingLightQuadAccessor {
    @Invoker("getX") float stardewcraft$getX(int vertex);
    @Invoker("getY") float stardewcraft$getY(int vertex);
    @Invoker("getZ") float stardewcraft$getZ(int vertex);
}
