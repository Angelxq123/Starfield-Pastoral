package com.stardew.craft.client.gui;

/** Logical pixels; content scrolls while the title and final actions remain visible. */
public record FarmSetupLayout(int x, int y, int width, int height, boolean columns, int actionHeight) {
    public static FarmSetupLayout fit(int width, int height, boolean profileOnly) {
        int w = Math.min(profileOnly ? 368 : 512, Math.max(1, width - 16));
        int h = Math.min(400, Math.max(1, height - 16));
        return new FarmSetupLayout((width - w) / 2, (height - h) / 2, w, h, !profileOnly && w >= 464, 22);
    }

    public FarmSetupLayout withActionHeight(int height) {
        return new FarmSetupLayout(x, y, width, this.height, columns, Math.max(22, height));
    }

    public FarmSetupLayout withContentHeight(int contentHeight) {
        int h = Math.min(height, Math.max(184, contentHeight + 40 + 8 + actionHeight + 12));
        return new FarmSetupLayout(x, y + (height - h) / 2, width, h, columns, actionHeight);
    }

    public int contentX() { return x + 14; }
    public int contentWidth() { return width - 36; }
    public int bodyTop() { return y + 40; }
    public int bodyBottom() { return footerY() - 8; }
    public int bodyHeight() { return bodyBottom() - bodyTop(); }
    public int footerY() { return y + height - actionHeight - 12; }
    public int columnWidth() { return columns ? (contentWidth() - 24) * 53 / 100 : contentWidth(); }
    public int rightWidth() { return columns ? contentWidth() - columnWidth() - 24 : contentWidth(); }
    public int primaryWidth() { return Math.min(144, (width - 36) / 2); }
    public int rightX() { return columns ? contentX() + columnWidth() + 24 : contentX(); }
    public int maxScroll(int contentHeight) { return Math.max(0, contentHeight - bodyHeight()); }
}
