package com.stardew.craft.client.animal;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.stardew.craft.animal.runtime.LivestockProductEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;

/** Uses the actual ItemStack model/quality pipeline without exposing a vanilla pickup entity. */
public final class LivestockProductRenderer extends EntityRenderer<LivestockProductEntity> {
    private final ItemRenderer items;
    public LivestockProductRenderer(EntityRendererProvider.Context context) { super(context); items = context.getItemRenderer(); shadowRadius = .1f; }
    @Override public ResourceLocation getTextureLocation(LivestockProductEntity entity) { return TextureAtlas.LOCATION_BLOCKS; }
    @Override public void render(LivestockProductEntity entity, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        pose.pushPose(); pose.translate(0, .12, 0); pose.mulPose(Axis.YP.rotationDegrees(45));
        items.renderStatic(entity.getItem(), ItemDisplayContext.GROUND, light, OverlayTexture.NO_OVERLAY, pose, buffers, entity.level(), entity.getId());
        pose.popPose(); super.render(entity, yaw, partialTick, pose, buffers, light);
    }
}
