package com.stardew.craft.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.stardew.craft.client.font.StardewFonts;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/** Mod messages and item labels hosted by vanilla's action bar also follow reading size. */
@Mixin(Gui.class)
public abstract class StardewReadingHudTextMixin {
    @WrapOperation(method = {"renderOverlayMessage", "renderSelectedItemName"}, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;drawStringWithBackdrop(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIII)I"))
    private int stardewcraft$readingText(GuiGraphics g, Font font, Component text, int x, int y, int width, int color,
                                       Operation<Integer> original) {
        float requested = StardewFonts.readingScale();
        if (requested == 1 || !stardewcraft$modText(text)) {
            return original.call(g, font, text, x, y, width, color);
        }
        float scale = Math.min(requested, Math.max(1, g.guiWidth() - 20) / (float) Math.max(1, width));
        float center = x + width / 2.0F;
        g.pose().pushPose();
        g.pose().translate(center, y, 0);
        g.pose().scale(scale, scale, 1);
        g.pose().translate(-center, -y, 0);
        try { return original.call(g, font, text, x, y, width, color); }
        finally { g.pose().popPose(); }
    }

    @Unique private static boolean stardewcraft$modText(Component text) {
        if (text.getContents() instanceof TranslatableContents translation) {
            if (translation.getKey().contains("stardewcraft")) return true;
            for (Object argument : translation.getArgs()) {
                if (argument instanceof Component child && stardewcraft$modText(child)) return true;
            }
        }
        return text.getSiblings().stream().anyMatch(StardewReadingHudTextMixin::stardewcraft$modText);
    }
}
