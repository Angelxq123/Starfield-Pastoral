package com.stardew.craft.client.gui.common;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/** SDV menu-texture tooltip with SDV font metrics and screen-edge clamping. */
public final class SdvTooltipRenderer {
    private static final int TEXT_COLOR = 0xFF5B3A1A;

    private SdvTooltipRenderer() {
    }

    public static void draw(GuiGraphics graphics, Font font, Component text,
                            int mouseX, int mouseY, int screenWidth, int screenHeight,
                            float guiScale, String languageCode) {
        float textScale = SdvFontAdapter.scale(
            font, languageCode, guiScale, SdvFontAdapter.Style.SMALL);
        int padding = Math.max(6, Math.round(16.0F / guiScale));
        int availableWidth = Math.max(1, screenWidth - 8 - padding * 2);
        int sourceToGui320 = Math.min(availableWidth, Math.max(80, Math.round(320.0F / guiScale)));
        int lineStep = SdvFontAdapter.lineStep(languageCode, guiScale, SdvFontAdapter.Style.SMALL);
        int wrapWidth = Math.max(1, (int) Math.floor(sourceToGui320 / textScale));
        List<FormattedCharSequence> lines = font.split(text, wrapWidth);
        while (lines.size() * lineStep + padding * 2 > screenHeight - 8 && sourceToGui320 < availableWidth) {
            sourceToGui320 = Math.min(availableWidth, sourceToGui320 * 2);
            lines = font.split(text, Math.max(1, (int) (sourceToGui320 / textScale)));
        }

        int textWidth = 0;
        for (FormattedCharSequence line : lines) {
            textWidth = Math.max(textWidth, Math.round(font.width(line) * textScale));
        }
        int boxWidth = textWidth + padding * 2;
        int boxHeight = Math.max(lineStep, lines.size() * lineStep) + padding * 2;

        beginFittedBox(graphics, mouseX, mouseY, screenWidth, screenHeight, boxWidth, boxHeight, guiScale);
        int x = 0;
        int y = 0;

        CommonGuiTextures.drawMenuTextureBox(
            graphics, x, y, boxWidth, boxHeight, 1.0F / guiScale, true);
        int textY = y + padding;
        for (FormattedCharSequence line : lines) {
            SdvFontAdapter.draw(graphics, font, line, x + padding, textY, textScale, TEXT_COLOR);
            textY += lineStep;
        }
        graphics.pose().popPose();
    }

    public static void drawAnimalShop(GuiGraphics graphics, Font font,
                                      Component title, Component description, int price,
                                      int mouseX, int mouseY, int screenWidth, int screenHeight,
                                      float guiScale, String languageCode) {
        float titleScale = SdvFontAdapter.scale(
            font, languageCode, guiScale, SdvFontAdapter.Style.DIALOGUE);
        float bodyScale = SdvFontAdapter.scale(
            font, languageCode, guiScale, SdvFontAdapter.Style.SMALL);
        int padding = Math.max(6, Math.round(16.0F / guiScale));
        int availableWidth = Math.max(1, screenWidth - 8 - padding * 2);
        int wrapGuiWidth = Math.min(availableWidth, Math.max(80, Math.round(320.0F / guiScale)));
        int wrapFontWidth = Math.max(1, (int) Math.floor(wrapGuiWidth / bodyScale));
        List<FormattedCharSequence> lines = font.split(description, wrapFontWidth);

        List<FormattedCharSequence> titleLines = font.split(title, Math.max(1, (int) (availableWidth / titleScale)));
        int titleWidth = 0;
        for (FormattedCharSequence line : titleLines) titleWidth = Math.max(titleWidth, Math.round(font.width(line) * titleScale));
        int bodyWidth = 0;
        for (FormattedCharSequence line : lines) {
            bodyWidth = Math.max(bodyWidth, Math.round(font.width(line) * bodyScale));
        }
        Component priceText = Component.literal(Integer.toString(Math.max(0, price)));
        int priceWidth = SdvFontAdapter.width(font, priceText, bodyScale)
            + Math.max(18, Math.round(64.0F / guiScale));
        int contentWidth = Math.max(titleWidth, Math.max(bodyWidth, priceWidth));

        int titleStep = SdvFontAdapter.lineStep(
            languageCode, guiScale, SdvFontAdapter.Style.DIALOGUE);
        int bodyStep = SdvFontAdapter.lineStep(
            languageCode, guiScale, SdvFontAdapter.Style.SMALL);
        int separatorGap = Math.max(4, Math.round(12.0F / guiScale));
        int priceRowHeight = Math.max(bodyStep, Math.round(44.0F / guiScale));
        int boxWidth = contentWidth + padding * 2;
        int boxHeight = padding * 2 + titleLines.size() * titleStep + separatorGap
            + Math.max(bodyStep, lines.size() * bodyStep) + separatorGap + separatorGap / 2 + priceRowHeight;

        beginFittedBox(graphics, mouseX, mouseY, screenWidth, screenHeight, boxWidth, boxHeight, guiScale);
        int x = 0;
        int y = 0;

        CommonGuiTextures.drawMenuTextureBox(
            graphics, x, y, boxWidth, boxHeight, 1.0F / guiScale, true);
        int textX = x + padding;
        int cursorY = y + padding;
        for (FormattedCharSequence line : titleLines) {
            SdvFontAdapter.draw(graphics, font, line, textX, cursorY, titleScale, TEXT_COLOR);
            cursorY += titleStep;
        }
        drawSeparator(graphics, textX, cursorY, contentWidth, guiScale);
        cursorY += separatorGap;
        for (FormattedCharSequence line : lines) {
            SdvFontAdapter.draw(graphics, font, line, textX, cursorY, bodyScale, TEXT_COLOR);
            cursorY += bodyStep;
        }
        cursorY += separatorGap / 2;
        drawSeparator(graphics, textX, cursorY, contentWidth, guiScale);
        cursorY += separatorGap;
        SdvFontAdapter.draw(graphics, font, priceText, textX, cursorY, bodyScale, TEXT_COLOR);
        int coinX = textX + SdvFontAdapter.width(font, priceText, bodyScale)
            + Math.max(4, Math.round(12.0F / guiScale));
        CommonGuiTextures.drawShopCoin(graphics, coinX, cursorY, 4.0F / guiScale);
        graphics.pose().popPose();
    }

    private static void beginFittedBox(GuiGraphics graphics, int mouseX, int mouseY,
                                       int screenWidth, int screenHeight, int boxWidth, int boxHeight, float guiScale) {
        float fit = GuiLayoutMath.fitTextScale(1.0F, boxWidth, boxHeight, screenWidth - 8, screenHeight - 8);
        float offset = Math.max(8, Math.round(32.0F / guiScale));
        float x = Math.max(4, Math.min(mouseX + offset, screenWidth - 4 - boxWidth * fit));
        float y = Math.max(4, Math.min(mouseY + offset, screenHeight - 4 - boxHeight * fit));
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        graphics.pose().scale(fit, fit, 1);
    }

    private static void drawSeparator(GuiGraphics graphics, int x, int y, int width, float guiScale) {
        int thickness = Math.max(1, Math.round(2.0F / guiScale));
        graphics.fill(x, y, x + width, y + thickness, 0x80CF9367);
    }
}
