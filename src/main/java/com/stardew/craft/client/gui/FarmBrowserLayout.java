package com.stardew.craft.client.gui;

/** Whole, generously spaced rows; search and final actions stay outside the scrolling list. */
public record FarmBrowserLayout(int x, int y, int width, int height, int rowHeight,
                                int visibleRows, int actionHeight, int lineHeight) {
    public static FarmBrowserLayout fit(int sw, int sh, int lines, int actionHeight, int count) {
        int w = Math.min(440, sw - 16);
        int row = Math.max(44, lines * 2 + 24);
        int fixed = 66 + 8 + lines * 2 + 10 + actionHeight + 12;
        int capacity = Math.max(1, Math.min(5, (sh - 16 - fixed) / row));
        int visible = Math.min(capacity, Math.max(1, count));
        int h = fixed + visible * row;
        return new FarmBrowserLayout((sw - w) / 2, Math.max(8, (sh - h) / 2), w, h, row, visible, actionHeight, lines);
    }
    public int left() { return x + 14; }
    public int innerWidth() { return width - 28; }
    public int rowWidth() { return innerWidth() - 14; }
    public int permissionWidth(int labelWidth) { return Math.min(rowWidth() - 120, Math.max(56, labelWidth + 18)); }
    public int rowTextWidth(int permissionWidth) { return rowWidth() - permissionWidth - 76; }
    public int scrollbarX() { return left() + innerWidth() - 8; }
    public int searchY() { return y + 38; }
    public int listY() { return y + 66; }
    public int listHeight() { return visibleRows * rowHeight; }
    public int listBottom() { return listY() + listHeight(); }
    public int statusY() { return listBottom() + 8; }
    public int footerY() { return y + height - actionHeight - 12; }
}
