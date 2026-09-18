package com.stardew.craft.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.stardew.craft.client.interior.TownDoorClient;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Prevents the door frame from becoming a one-frame full-screen block overlay during traversal. */
@Mixin(ScreenEffectRenderer.class)
public abstract class TownDoorScreenEffectMixin {
    @Inject(method = "renderTex", at = @At("HEAD"), cancellable = true)
    private static void stardewcraft$skipDoorwayBlockOverlay(TextureAtlasSprite sprite, PoseStack pose,
                                                              CallbackInfo ci) {
        if (TownDoorClient.suppressBlockOverlay()) ci.cancel();
    }
}
