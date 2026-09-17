package com.stardew.craft.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.stardew.craft.blockentity.ShippingBinBlockEntity;
import com.stardew.craft.client.model.block.ShippingBinGeoModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.layer.BlockAndItemGeoLayer;

public class ShippingBinBlockEntityRenderer extends StardewGeoBlockRenderer<ShippingBinBlockEntity> {
    public ShippingBinBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
        super(new ShippingBinGeoModel());
        addRenderLayer(new BlockAndItemGeoLayer<ShippingBinBlockEntity>(this,
                (bone, bin) -> bone.getName().equals("shipment_item") && !bin.shipmentItem().isEmpty() ? bin.shipmentItem() : null,
                (bone, bin) -> null) {
            @Override protected ItemDisplayContext getTransformTypeForStack(GeoBone bone, ItemStack stack, ShippingBinBlockEntity bin) {
                return ItemDisplayContext.NONE;
            }
            @Override protected void renderStackForBone(PoseStack pose, GeoBone bone, ItemStack stack,
                    ShippingBinBlockEntity bin, MultiBufferSource buffers, float partialTick, int light, int overlay) {
                float alpha = Math.max(0, 1 - bin.shipmentAge(partialTick) / .38f);
                MultiBufferSource fading = type -> {
                    // Item atlas quads need blending rather than alpha-test-only disappearance.
                    var target = type == Sheets.cutoutBlockSheet() || type == Sheets.solidBlockSheet()
                            ? Sheets.translucentItemSheet() : type;
                    return new FadingVertexConsumer(buffers.getBuffer(target), alpha);
                };
                super.renderStackForBone(pose, bone, stack, bin, fading, partialTick, light, overlay);
            }
        });
    }

    private record FadingVertexConsumer(VertexConsumer delegate, float alpha) implements VertexConsumer {
        @Override public VertexConsumer addVertex(float x, float y, float z) { delegate.addVertex(x, y, z); return this; }
        @Override public VertexConsumer setColor(int r, int g, int b, int a) { delegate.setColor(r, g, b, (int)(a * alpha)); return this; }
        @Override public VertexConsumer setUv(float u, float v) { delegate.setUv(u, v); return this; }
        @Override public VertexConsumer setUv1(int u, int v) { delegate.setUv1(u, v); return this; }
        @Override public VertexConsumer setUv2(int u, int v) { delegate.setUv2(u, v); return this; }
        @Override public VertexConsumer setNormal(float x, float y, float z) { delegate.setNormal(x, y, z); return this; }
    }
}
