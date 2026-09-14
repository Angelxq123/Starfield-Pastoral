package com.stardew.craft.client.gui.common;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

public final class SdvTexture {
    private final ResourceLocation texture;
    private final int width;
    private final int height;
    private final int textureWidth;
    private final int textureHeight;

    private SdvTexture(ResourceLocation texture, int width, int height, int textureWidth, int textureHeight) {
        this.texture = texture;
        this.width = width;
        this.height = height;
        this.textureWidth = textureWidth;
        this.textureHeight = textureHeight;
    }

    public static SdvTexture full(ResourceLocation texture, int width, int height) {
        return new SdvTexture(texture, width, height, width, height);
    }

    public void drawPixelZoom(GuiGraphics graphics, int x, int y, float scale) {
        drawPixelZoomTint(graphics, x, y, scale, 1.0F, 1.0F, 1.0F, 1.0F);
    }

    public void drawAtCurrentPose(GuiGraphics graphics, int x, int y) {
        graphics.blit(texture, x, y, 0, 0, width, height, textureWidth, textureHeight);
    }

    public void drawAtCurrentPoseTint(GuiGraphics graphics, int x, int y, float red, float green, float blue, float alpha) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        graphics.setColor(red, green, blue, alpha);
        drawAtCurrentPose(graphics, x, y);
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    public void drawStretchedTint(GuiGraphics graphics, int x, int y, int stretchedWidth, int stretchedHeight, float red, float green, float blue, float alpha) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        graphics.setColor(red, green, blue, alpha);
        graphics.blit(texture, x, y, stretchedWidth, stretchedHeight, 0, 0, width, height, textureWidth, textureHeight);
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    public void drawPixelZoomTint(GuiGraphics graphics, int x, int y, float scale, float red, float green, float blue, float alpha) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        graphics.setColor(red, green, blue, alpha);
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.blit(texture, 0, 0, 0, 0, width, height, textureWidth, textureHeight);
        graphics.pose().popPose();
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
    }
}
