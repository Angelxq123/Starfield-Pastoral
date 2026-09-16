package com.stardew.craft.client.gui;

/** Native GUI pixels. An open cookbook when space permits, single leaves on small screens. */
public final class CookingLayout {
    private CookingLayout() { }
    public record Page(int x, int y, int width, int height, boolean wide, int contentX,
                       int contentWidth, int bodyTop, int bodyBottom, int listX, int listWidth, int detailX,
                       int detailWidth, int inventoryY, int footerY) { }
    public static Page fit(int width, int height, int headerHeight, int toolbarHeight, int footerHeight) {
        int w = Math.min(456, width - 12), h = Math.min(364, height - 12);
        int x = (width - w) / 2, y = (height - h) / 2, cw = w - 32;
        boolean wide = cw >= 382 && h >= 340 + 8 * Math.max(0, footerHeight - 24);
        int inventoryY = wide ? y + h - 86 : -1;
        int bottom = wide ? inventoryY - 18 : y + h - footerHeight - 18;
        int dw = wide ? 160 : cw;
        return new Page(x, y, w, h, wide, x + 16, cw, y + headerHeight + 12,
                bottom, wide ? x + 16 + dw + 28 : x + 16, wide ? cw - dw - 28 : cw,
                x + 16, dw, inventoryY, y + h - footerHeight - 10);
    }
    public record Grid(int columns, int rows, int cell, int startX) { }
    public static Grid grid(int x, int width, int height) {
        int cell = 28, cols = Math.max(1, (width - 8) / cell), rows = Math.max(1, height / cell);
        return new Grid(cols, rows, cell, x + (width - 8 - cols * cell) / 2);
    }
    public static int ingredientColumns(int width, int countWidth) { return Math.max(1, Math.min(4, width / Math.max(40, countWidth + 8))); }
    public static int clampScroll(int scroll, int count, int visible) { return Math.max(0, Math.min(scroll, Math.max(0, count - visible))); }
}
