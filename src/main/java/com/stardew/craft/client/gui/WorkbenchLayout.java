package com.stardew.craft.client.gui;

/** Shared logical-pixel geometry for all three workstations. */
public record WorkbenchLayout(int x, int y, int width, int height,
                              int gridX, int gridY, int columns, int rows,
                              int detailX, int detailWidth, int statusHeight) {
    public static final int CELL = 24;
    public static final int PITCH = 26;

    public static WorkbenchLayout fit(int screenWidth, int screenHeight) {
        return fit(screenWidth, screenHeight, 10);
    }

    public static WorkbenchLayout fit(int screenWidth, int screenHeight, int fontHeight) {
        int w = Math.min(480, Math.max(1, screenWidth - 16));
        int h = Math.min(300, Math.max(1, screenHeight - 16));
        int x = (screenWidth - w) / 2;
        int y = (screenHeight - h) / 2;
        int detail = Math.max(116, Math.min(164, Math.round((w - 36) * .38f)));
        int columns = Math.max(1, (w - 36 - detail) / PITCH);
        int rows = Math.max(1, (h - 88) / PITCH);
        return new WorkbenchLayout(x, y, w, h, x + 12, y + 56,
                columns, rows, x + w - 12 - detail, detail, Math.max(22, fontHeight * 2 + 2));
    }

    public int capacity() { return columns * rows; }
    public int gridWidth() { return columns * PITCH - 2; }
    public int gridHeight() { return rows * PITCH - 2; }
    public int bottom() { return y + height - 12; }
    public int quantityY() { return statusY() - 48; }
    public int craftY() { return statusY() - 24; }
    public int statusY() { return bottom() - statusHeight; }
}
