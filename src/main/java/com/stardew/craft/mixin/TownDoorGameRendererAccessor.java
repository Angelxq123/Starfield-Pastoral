package com.stardew.craft.mixin;

import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Render-context field access adapted from Immersive Portals (Apache-2.0). */
@Mixin(GameRenderer.class)
public interface TownDoorGameRendererAccessor {
    @Accessor("mainCamera") @Mutable
    void stardewcraft$setMainCamera(Camera camera);

    @Accessor("renderHand")
    boolean stardewcraft$rendersHand();
}
