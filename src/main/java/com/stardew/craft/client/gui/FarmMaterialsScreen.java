package com.stardew.craft.client.gui;

import com.stardew.craft.shop.CarpenterBlueprint;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

public final class FarmMaterialsScreen extends FarmFolioScreen {
    private final List<CarpenterBlueprint.MaterialEntry> materials;
    private final Component building;
    private final int price;
    private int page;

    public FarmMaterialsScreen(
            Screen parent,
            Component building,
            int price,
            List<CarpenterBlueprint.MaterialEntry> materials) {
        super(ui("materials"), parent);
        this.building = building;
        this.price = price;
        this.materials = FarmMaterialCosts.combined(materials);
    }

    @Override
    protected int preferredWidth() {
        return 340;
    }

    @Override
    protected void layout() {
        int count = Math.max(1, (h - 156) / 36);
        arrow(
                false,
                x + w / 2 - 64,
                y + h - 72,
                page > 0,
                () -> {
                    page--;
                    init();
                });
        arrow(
                true,
                x + w / 2 + 40,
                y + h - 72,
                (page + 1) * count < materials.size(),
                () -> {
                    page++;
                    init();
                });
        button(Component.translatable("gui.back"), x + 16, y + h - 34, w - 32, 24, this::onClose);
    }

    @Override
    protected void paint(GuiGraphics g) {
        paper(g, x + 16, y + 36, w - 32, h - 80);
        label(g, building, x + 28, y + 50, w - 160, INK);
        money(g, price, x + w - 122, y + 44, INK);
        rule(g, x + 28, y + 72, w - 56);
        int count = Math.max(1, (h - 156) / 36);
        for (int i = page * count; i < Math.min(materials.size(), (page + 1) * count); i++) {
            var m = materials.get(i);
            materialRow(g,m,x+28,y+86+(i-page*count)*36,w-56);
        }
        label(
                g,
                Component.literal(
                        (page + 1) + " / " + Math.max(1, (materials.size() + count - 1) / count)),
                x + w / 2 - 16,
                y + h - 64,
                60,
                MUTED);
    }
}
