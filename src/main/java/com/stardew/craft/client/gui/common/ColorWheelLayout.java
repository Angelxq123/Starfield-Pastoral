package com.stardew.craft.client.gui.common;

/** Pure canvas geometry, shared by painting, hit testing and headless layout checks. */
public record ColorWheelLayout(int x, int y) {
    public static final int OUTER = 72;
    public static final int INNER = 46;
    public static final int MARGIN = 6;
    public static final int PAGE_SIZE = 21;
    public static final int NONE = -2;
    public static final int ORIGINAL = -1;

    public static ColorWheelLayout beside(int width, int height, int containerLeft, int containerRight, int entryY) {
        int extent = OUTER + MARGIN;
        // The color entry and organize button occupy the same 18px side rail.
        // Leave that rail in place; when width is tight, tuck the round wheel below it.
        int right = containerRight + 30 + extent;
        int left = containerLeft - extent - 4;
        int center = clamp(right, extent, width - extent);
        int cy = clamp(entryY + OUTER, extent, height - extent);
        int railRight = containerRight + 25;
        int dx = Math.max(0, center - railRight);
        if (dx < extent) {
            int clearance = (int)Math.ceil(Math.sqrt(extent * extent - dx * dx)) + 4;
            int belowRail = entryY + 49 + clearance;
            if (belowRail <= height - extent) cy = Math.max(cy, belowRail);
            else center = clamp(left, extent, width - extent);
        }
        return new ColorWheelLayout(center, cy);
    }

    public int hit(double mouseX, double mouseY, int count) {
        double dx = mouseX - x, dy = mouseY - y;
        if (dx >= -25 && dx < 25 && dy >= 19 && dy < 35) return ORIGINAL;
        double radius = Math.hypot(dx, dy);
        if (count == 0 || radius < INNER || radius > OUTER + 3) return NONE;
        double step = Math.PI * 2 / count;
        double angle = Math.atan2(dy, dx) + Math.PI / 2 + step / 2;
        angle = (angle % (Math.PI * 2) + Math.PI * 2) % (Math.PI * 2);
        double local = angle % step;
        if (Math.min(local, step - local) * radius < 1.2) return NONE;
        return Math.min(count - 1, (int) (angle / step));
    }

    public static double angle(int index, int count) { return -Math.PI / 2 + index * Math.PI * 2 / count; }
    public boolean contains(double mx, double my) { return Math.hypot(mx - x, my - y) <= OUTER + MARGIN; }
    public static float ease(float t) { float a = 1 - Math.max(0, Math.min(1, t)); return 1 - a * a * a * a; }
    private static int clamp(int n, int low, int high) { return Math.max(low, Math.min(Math.max(low, high), n)); }
}
