package com.stardew.craft.mixin;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;
@Pseudo
@Mixin(targets="net.caffeinemc.mods.sodium.client.render.frapi.render.AbstractBlockRenderContext",remap=false)
public interface SodiumColoredLightPositionAccessor {
    @Accessor("pos") BlockPos stardewcraft$getLightPosition();
}
