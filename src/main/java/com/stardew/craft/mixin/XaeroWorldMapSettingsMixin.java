package com.stardew.craft.mixin;

import com.stardew.craft.integration.xaero.XaeroMapMaterials;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "xaero.map.settings.ModSettings", remap = false)
public abstract class XaeroWorldMapSettingsMixin {
    @Inject(method = "getRegionCacheHashCode", at = @At("RETURN"), cancellable = true)
    private void stardewcraft$includeSeasonInSavedMapColours(CallbackInfoReturnable<Integer> callback) {
        callback.setReturnValue(XaeroMapMaterials.regionCacheHash(callback.getReturnValueI(), XaeroMapMaterials.season()));
    }
}
