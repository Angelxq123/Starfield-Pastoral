package com.stardew.craft.client.gui.common;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.math.Axis;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.blockentity.StoneChestBlockEntity;
import com.stardew.craft.blockentity.WoodenChestBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/** Isolated preview entities never have a level, inventory, position or network side effect. */
@OnlyIn(Dist.CLIENT)
public final class ChestModelPreview {
    private WoodenChestBlockEntity wood;
    private StoneChestBlockEntity stone;

    public void draw(GuiGraphics graphics, int x, int y, int color, boolean wooden, boolean stoneChest) {
        if (!wooden && !stoneChest) {
            // A reused chest menu may describe an addon's model. Do not pretend it is our wood chest.
            graphics.blit(ResourceLocation.fromNamespaceAndPath("stardewcraft", "textures/gui/color_wheel.png"),
                    x - 12, y - 12, 0, 0, 24, 24, 24, 24);
            int rgb = com.stardew.craft.block.utility.WoodenChestColorPalette.rgbAt(color);
            graphics.fill(x - 13, y + 14, x + 13, y + 18, 0xFF000000 | rgb);
            return;
        }
        if (wooden && wood == null) wood = new WoodenChestBlockEntity(BlockPos.ZERO, ModBlocks.WOODEN_CHEST.get().defaultBlockState());
        if (stoneChest && stone == null) stone = new StoneChestBlockEntity(BlockPos.ZERO, ModBlocks.STONE_CHEST.get().defaultBlockState());
        if (wooden) wood.setColorSelection(color); else stone.setColorSelection(color);
        graphics.flush();
        var pose = graphics.pose();
        var buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        pose.pushPose();
        try {
            pose.translate(x, y, 70);
            pose.scale(29, -29, 29);
            pose.mulPose(Axis.XP.rotationDegrees(23));
            // GUI depth faces +Z; the authored latch is on -Z. Turn the front toward the viewer.
            pose.mulPose(Axis.YP.rotationDegrees(145));
            pose.translate(-.5, -.5, -.5);
            Lighting.setupFor3DItems();
            Minecraft.getInstance().getBlockEntityRenderDispatcher().renderItem(wooden ? wood : stone,
                    pose, buffers, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
            buffers.endBatch();
        } finally {
            pose.popPose();
            Lighting.setupFor3DItems();
        }
    }
}
