package com.stardew.craft.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.stardew.craft.client.npcnative.NativeNpcAssets;
import com.stardew.craft.client.npcnative.NativeSamRenderer;
import com.stardew.craft.entity.npc.StardewNpcEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

/** Current native models own migrated characters; legacy rendering is only for unmigrated IDs. */
public final class NpcRenderer extends EntityRenderer<StardewNpcEntity> {
    private final NpcGeoRenderer legacy;
    private final EntityRendererProvider.Context context;
    private final java.util.Map<String,NativeSamRenderer<StardewNpcEntity>> nativeRenderers;

    public NpcRenderer(EntityRendererProvider.Context context) {
        super(context);
        legacy = new NpcGeoRenderer(context);
        this.context = context;
        nativeRenderers = new java.util.HashMap<>();
        shadowRadius = 0.35F;
    }

    private boolean useNative(StardewNpcEntity entity) {
        String id = NativeNpcAssets.renderId(entity.getNpcId());
        if (NativeNpcAssets.model(id) == null) return false;
        nativeRenderers.computeIfAbsent(id,key -> new NativeSamRenderer<>(context,key));
        return true;
    }

    @Override
    public boolean shouldRender(StardewNpcEntity entity, Frustum frustum, double x, double y, double z) {
        if (NpcGeoRenderer.isHiddenForLocalPlayer(entity.getNpcId())) return false;
        return useNative(entity) ? super.shouldRender(entity, frustum, x, y, z)
                : legacy.shouldRender(entity, frustum, x, y, z);
    }

    @Override
    public ResourceLocation getTextureLocation(StardewNpcEntity entity) {
        return useNative(entity) ? nativeRenderers.get(NativeNpcAssets.renderId(entity.getNpcId())).getTextureLocation(entity) : legacy.getTextureLocation(entity);
    }

    @Override
    public void render(StardewNpcEntity entity, float yaw, float partialTick, PoseStack stack,
                       MultiBufferSource buffers, int light) {
        if (NpcGeoRenderer.isHiddenForLocalPlayer(entity.getNpcId())) return;
        if (useNative(entity)) {
            nativeRenderers.get(NativeNpcAssets.renderId(entity.getNpcId())).render(entity, yaw, partialTick, stack, buffers, light);
            legacy.renderOverheadIndicator(entity, stack, buffers, light);
        } else {
            legacy.render(entity, yaw, partialTick, stack, buffers, light);
        }
    }
}
