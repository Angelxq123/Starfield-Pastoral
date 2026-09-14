package com.stardew.craft.integration.xaero;

import com.stardew.craft.client.model.terrain.TerrainSeasonTextures;
import com.stardew.craft.templates.TemplateBlock;
import com.stardew.craft.templates.TemplateBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;

/** Map sampling needs a material per position, before Xaero caches a colour per state. */
public final class XaeroMapMaterials {
    public static BlockState resolve(BlockState original, BlockGetter level, BlockPos pos) {
        if (original.getBlock() instanceof TemplateBlock
                && level.getBlockEntity(pos) instanceof TemplateBlockEntity template) {
            BlockState material = template.material();
            if (material != null && !(material.getBlock() instanceof TemplateBlock)) return material;
        }
        return original;
    }

    public static int season() {
        return TerrainSeasonTextures.currentTextureSet();
    }

    /** Persist the season in Xaero's own region cache key; refresh stays incremental. */
    public static int regionCacheHash(int original, int season) {
        return 31 * original + 0x53444300 + season;
    }

    private XaeroMapMaterials() {}
}
