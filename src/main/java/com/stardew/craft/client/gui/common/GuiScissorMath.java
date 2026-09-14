package com.stardew.craft.client.gui.common;

import org.joml.Matrix4fc;

/** Clips are transformed once, at push time, and intersected in framebuffer pixels. */
public final class GuiScissorMath {
    private GuiScissorMath() {}

    public record Bounds(int left, int top, int right, int bottom) {}

    public static Bounds framebuffer(Matrix4fc pose, double scale, int left, int top, int right, int bottom) {
        double x1 = x(pose, scale, left, top), y1 = y(pose, scale, left, top);
        if (right <= left || bottom <= top) {
            int x = floor(x1), y = floor(y1);
            return new Bounds(x, y, x, y);
        }
        double x2 = x(pose, scale, right, top), y2 = y(pose, scale, right, top);
        double x3 = x(pose, scale, left, bottom), y3 = y(pose, scale, left, bottom);
        double x4 = x(pose, scale, right, bottom), y4 = y(pose, scale, right, bottom);
        return new Bounds(floor(Math.min(Math.min(x1, x2), Math.min(x3, x4))),
                floor(Math.min(Math.min(y1, y2), Math.min(y3, y4))),
                ceil(Math.max(Math.max(x1, x2), Math.max(x3, x4))),
                ceil(Math.max(Math.max(y1, y2), Math.max(y3, y4))));
    }

    public static int pointX(Matrix4fc pose, double scale, int x, int y) { return floor(x(pose, scale, x, y)); }
    public static int pointY(Matrix4fc pose, double scale, int x, int y) { return floor(y(pose, scale, x, y)); }

    private static double x(Matrix4fc p, double scale, int x, int y) {
        return ((double) p.m00() * x + (double) p.m10() * y + p.m30()) * scale;
    }

    private static double y(Matrix4fc p, double scale, int x, int y) {
        return ((double) p.m01() * x + (double) p.m11() * y + p.m31()) * scale;
    }

    // Float PoseStack multiplication can put an integral edge a few ten-thousandths
    // of a pixel past its boundary. Snap only numerical noise, then round outwards.
    private static double snap(double value) {
        double integer = Math.rint(value);
        return Math.abs(value - integer) < 0.001 ? integer : value;
    }

    private static int floor(double value) { return (int) Math.floor(snap(value)); }
    private static int ceil(double value) { return (int) Math.ceil(snap(value)); }
}
