package com.stardew.craft.mixin;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Invoker;
@Pseudo
@Mixin(targets="net.caffeinemc.mods.sodium.client.render.frapi.mesh.QuadViewImpl",remap=false)
public interface SodiumColoredLightQuadAccessor {
    @Invoker("getX") float stardewcraft$getX(int vertex);
    @Invoker("getY") float stardewcraft$getY(int vertex);
    @Invoker("getZ") float stardewcraft$getZ(int vertex);
    @Invoker("getLight") int stardewcraft$getLight(int vertex);
    @Invoker("getLightFace") Direction stardewcraft$getLightFace();
    @Invoker("hasShade") boolean stardewcraft$hasShade();
}
