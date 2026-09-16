package com.stardew.craft.mixin;

import com.stardew.craft.integration.xaero.XaeroMapMaterials;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "xaero.map.MapWriter", remap = false)
public abstract class XaeroWorldMapWriterMixin {
    @Unique private int stardewcraft$colourSeason = -1;
    @Shadow public abstract void requestCachedColoursClear();

    @Inject(method = "unpackFramedBlocks", at = @At("HEAD"), cancellable = true)
    private void stardewcraft$resolveTemplate(BlockState state, Level level, BlockPos pos,
                                              CallbackInfoReturnable<BlockState> callback) {
        BlockState material = XaeroMapMaterials.resolve(state, level, pos);
        if (material != state) callback.setReturnValue(material);
    }

    @Inject(method = "loadBlockColourFromTexture", at = @At("HEAD"))
    private void stardewcraft$invalidateSeasonColours(CallbackInfoReturnable<Integer> callback) {
        int season = XaeroMapMaterials.season();
        if (season != stardewcraft$colourSeason) {
            stardewcraft$colourSeason = season;
            // Run the writer's own clear path before its last-state fast cache lookup.
            requestCachedColoursClear();
        }
    }
}
