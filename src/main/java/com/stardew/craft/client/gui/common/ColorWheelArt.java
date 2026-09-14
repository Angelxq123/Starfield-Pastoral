package com.stardew.craft.client.gui.common;

import java.util.ArrayList;
import java.util.List;

/** Pixel-aligned, material-shaped paint spans. Built once per page, never per frame. */
public final class ColorWheelArt {
    public record Span(int x, int y, int width, int color) {}
    private ColorWheelArt() {}

    public static List<Span> petal(int index, int count, int rgb) {
        List<Span> spans = new ArrayList<>();
        for (int y = -72; y < 72; y++) {
            int start = -72, previous = pixel(-71.5, y + .5, index, count, rgb);
            for (int x = -71; x <= 72; x++) {
                int next = x == 72 ? 0 : pixel(x + .5, y + .5, index, count, rgb);
                if (next != previous) {
                    if (previous != 0) spans.add(new Span(start, y, x - start, previous));
                    start = x; previous = next;
                }
            }
        }
        return List.copyOf(spans);
    }

    private static int pixel(double x, double y, int index, int count, int rgb) {
        double radius = Math.hypot(x, y);
        double delta = Math.atan2(y, x) - ColorWheelLayout.angle(index, count);
        delta = Math.atan2(Math.sin(delta), Math.cos(delta));
        double edge = (Math.PI / count - Math.abs(delta)) * radius;
        if (radius < 46 || radius >= 72 || edge < 1.2) return 0;
        if (radius < 47.5 || radius > 70.5 || edge < 2.3) return 0xFF4B302A;
        if (radius > 68.8 || (delta < 0 && edge < 3.7)) return mix(rgb, 0xFFF0BC, .43);
        if (radius < 49.5 || edge < 3.7) return mix(rgb, 0x392726, .32);
        return mix(rgb, y < -10 ? 0xFFF0D0 : 0x4B3430, y < -10 ? .08 : .05);
    }

    private static int mix(int a, int b, double t) {
        int out = 0xFF000000;
        for (int shift : new int[]{16, 8, 0}) {
            out |= (int)Math.round(((a >> shift) & 255) * (1 - t) + ((b >> shift) & 255) * t) << shift;
        }
        return out;
    }
}
