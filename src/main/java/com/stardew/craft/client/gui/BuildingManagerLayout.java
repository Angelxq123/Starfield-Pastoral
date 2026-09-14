package com.stardew.craft.client.gui;

/** GUI-pixel geometry shared with headless layout proofs. */
public final class BuildingManagerLayout {
    private BuildingManagerLayout() { }
    public record Page(int x, int y, int width, int height, int contentX, int contentWidth,
                       int bodyTop, int bodyBottom, boolean columns, int ledgerX, int ledgerWidth) { }
    public static Page page(int width, int height, int line, int footerHeight) {
        return page(width, height, line, footerHeight, 308);
    }
    public static Page page(int width, int height, int line, int footerHeight, int preferredHeight) {
        int w = Math.min(420, width - 12), h = Math.min(preferredHeight, height - 12);
        int x = (width - w) / 2, y = (height - h) / 2, cw = w - 44;
        boolean columns = cw >= 350;
        int ledger = columns ? 160 : 0;
        return new Page(x, y, w, h, x + 28, cw, y + Math.max(42, line + 26),
                y + h - 12 - footerHeight, columns, ledger, cw - ledger);
    }
    public static int labelWidth(int width, int countWidth) {
        return Math.max(1, width - 24 - countWidth - 24);
    }
    public static boolean stacked(int width, int countWidth) { return labelWidth(width, countWidth) < 72; }
    public record Row(int labelWidth, int countY, int height) { }
    public static Row row(int width, int countWidth, int labelHeight, int line) {
        boolean stacked = stacked(width, countWidth);
        return new Row(stacked ? width - 24 : labelWidth(width, countWidth),
                stacked ? labelHeight + 3 : 2,
                Math.max(22, labelHeight + (stacked ? line + 11 : 8)));
    }
    public record Scrollbar(int y, int height, int maximum, int top, int viewport) {
        public int at(double mouseY) {
            return Math.max(0, Math.min(maximum, (int) Math.round(
                    (mouseY - top - height / 2.0) * maximum / Math.max(1, viewport - height))));
        }
    }
    public static Scrollbar scrollbar(int top, int bottom, int content, int scroll) {
        int viewport = Math.max(1, bottom - top), max = Math.max(0, content - viewport);
        int thumb = Math.min(viewport, Math.max(16, viewport * viewport / Math.max(1, content)));
        int offset = (viewport - thumb) * Math.max(0, Math.min(max, scroll)) / Math.max(1, max);
        return new Scrollbar(top + offset, thumb, max, top, viewport);
    }
}
