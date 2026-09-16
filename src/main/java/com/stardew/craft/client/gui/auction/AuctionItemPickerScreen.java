package com.stardew.craft.client.gui.auction;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import java.util.function.IntConsumer;

/** Native 16px item renderers; slot widgets support both mouse and keyboard selection. */
@SuppressWarnings("null")
public class AuctionItemPickerScreen extends AuctionScreen {
    private final Screen parent;
    private final IntConsumer onPick;
    private int gridY;
    private boolean empty;
    public AuctionItemPickerScreen(Screen parent, IntConsumer onPick) {
        super("stardewcraft.auction.picker.title");
        this.parent = parent; this.onPick = onPick;
    }
    @Override protected int preferredWidth() { return 286; }
    @Override protected int preferredHeight() { return 240; }
    @Override protected void layout() {
        empty = java.util.stream.IntStream.range(0, 36).allMatch(slot -> inventory(slot).isEmpty());
        if (empty) {
            contentHeight = 80 + wrappedHeight(tr("picker.empty"), contentW)
                    + 8 + wrappedHeight(tr("picker.empty_hint"), contentW);
            footer(tr("picker.cancel"), false, false, this::onClose);
            return;
        }
        gridY = wrappedHeight(tr("picker.hint"), contentW) + 12;
        int columns = Math.min(9, Math.max(1, contentW / 22));
        int gridX = (contentW - columns * 22 + 2) / 2;
        int y = gridY;
        for (int section = 0; section < 2; section++) {
            int count = section == 0 ? 27 : 9;
            for (int i = 0; i < count; i++) {
                int slot = section == 0 ? i + 9 : i;
                inventoryButton(slot, gridX + i % columns * 22, y + i / columns * 22,
                        () -> { onPick.accept(slot); minecraft.setScreen(parent); });
            }
            y += ((count + columns - 1) / columns) * 22 + 8;
        }
        contentHeight = y;
        footer(tr("picker.cancel"), false, false, this::onClose);
    }
    @Override protected void updateState() {
        boolean nowEmpty = java.util.stream.IntStream.range(0, 36).allMatch(slot -> inventory(slot).isEmpty());
        if (empty != nowEmpty) rebuild();
    }
    @Override protected void drawBody(GuiGraphics g, float partialTick) {
        if (empty) {
            AuctionUi.illustration(g, "empty_crate", (contentW - 64) / 2, 2);
            int end = paragraph(g, tr("picker.empty"), 0, 80, contentW, AuctionUi.INK);
            paragraph(g, tr("picker.empty_hint"), 0, end + 8, contentW, AuctionUi.MUTED);
            return;
        }
        paragraph(g, tr("picker.hint"), 0, 0, contentW, AuctionUi.MUTED);
    }
    @Override public void onClose() { minecraft.setScreen(parent); }
}
