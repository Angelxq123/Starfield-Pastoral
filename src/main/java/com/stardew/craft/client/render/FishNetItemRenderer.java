package com.stardew.craft.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.blockentity.FishNetBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** Reuses the placed net's geometry, UVs and renderer in every item context. */
public class FishNetItemRenderer extends BlockEntityWithoutLevelRenderer {
    private FishNetBlockEntity net;

    public FishNetItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack pose,
                             MultiBufferSource buffers, int light, int overlay) {
        if (net == null) {
            net = new FishNetBlockEntity(BlockPos.ZERO, ModBlocks.FISH_NET.get().defaultBlockState());
        }
        Minecraft.getInstance().getBlockEntityRenderDispatcher().renderItem(net, pose, buffers, light, overlay);
    }
}
