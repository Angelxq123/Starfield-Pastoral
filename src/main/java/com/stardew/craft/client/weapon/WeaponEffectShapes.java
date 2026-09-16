package com.stardew.craft.client.weapon;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** Texture-free outlines shared by marks and the remaining legacy skill presentations. */
public final class WeaponEffectShapes {
    public enum Mark { HEAT, FROST, STAR, INFINITY, TIDE, VOW, BONE, LEAF }
    private static final Vec3 X = new Vec3(1, 0, 0), Y = new Vec3(0, 1, 0), Z = new Vec3(0, 0, 1);
    private WeaponEffectShapes() {}

    public static void ring(VertexConsumer out, Matrix4f pose, float radius, int r, int g, int b, int alpha) {
        WeaponGlowGeometry.ring(out, pose, Vec3.ZERO, X, Z, radius, radius * 0.025, r, g, b, alpha * 3 / 4);
        WeaponGlowGeometry.ring(out, pose, new Vec3(0, 0.005, 0), X, Z, radius * 0.96, radius * 0.008,
                235, 248, 255, alpha);
    }
    public static void crack(VertexConsumer out, Matrix4f pose, float halfLength, float halfWidth, int r, int g, int b, int alpha) {
        for (int i = 0; i < 12; i++) {
            Vec3 a = new Vec3(-halfLength + halfLength * i / 6, 0, i == 0 ? 0 : Math.sin(i * 2.7) * halfWidth * 0.5);
            Vec3 c = new Vec3(-halfLength + halfLength * (i + 1) / 6, 0, i == 11 ? 0 : Math.sin((i + 1) * 2.7) * halfWidth * 0.5);
            WeaponGlowGeometry.strip(out, pose, a, c, Z.scale(halfWidth * 0.16), r, g, b, alpha * 3 / 4);
            WeaponGlowGeometry.strip(out, pose, a.add(0, 0.003, 0), c.add(0, 0.003, 0), Z.scale(halfWidth * 0.035),
                    230, 244, 255, alpha);
        }
    }
    public static void mark(VertexConsumer out, Matrix4f pose, Mark kind, float size, int r, int g, int b, int alpha) {
        Matrix4f m = new Matrix4f(pose).scale(size);
        switch (kind) {
            case LEAF -> {
                line(out, m, 0, -0.9, 0.52, 0, r, g, b, alpha); line(out, m, 0.52, 0, 0, 0.9, r, g, b, alpha);
                line(out, m, 0, 0.9, -0.52, 0, r, g, b, alpha); line(out, m, -0.52, 0, 0, -0.9, r, g, b, alpha);
                line(out, m, 0, -0.9, 0, 0.8, r, g, b, alpha);
            }
            case HEAT -> {
                for (int i = -1; i <= 1; i++) line(out, m, i * 0.4 - 0.15, -0.7, i * 0.4 + 0.15, 0.7, r, g, b, alpha);
            }
            case FROST, STAR -> {
                int count = kind == Mark.FROST ? 6 : 4;
                for (int i = 0; i < count; i++) {
                    double a = i * Math.PI * 2 / count;
                    double x = Math.cos(a), y = Math.sin(a);
                    line(out, m, 0, 0, x, y, r, g, b, alpha);
                    if (kind == Mark.FROST) {
                        line(out, m, x * 0.6, y * 0.6, x * 0.43 - y * 0.2, y * 0.43 + x * 0.2, r, g, b, alpha);
                        line(out, m, x * 0.6, y * 0.6, x * 0.43 + y * 0.2, y * 0.43 - x * 0.2, r, g, b, alpha);
                    }
                }
            }
            case INFINITY, TIDE -> {
                for (int i = 0; i < 32; i++) {
                    double a = i * Math.PI * 2 / 32, c = (i + 1) * Math.PI * 2 / 32;
                    if (kind == Mark.INFINITY) line(out, m, Math.cos(a), Math.sin(2 * a) * 0.45, Math.cos(c), Math.sin(2 * c) * 0.45, r, g, b, alpha);
                    else for (int j = -1; j <= 1; j += 2) line(out, m, a / Math.PI - 1, Math.sin(a) * 0.2 + j * 0.3,
                            c / Math.PI - 1, Math.sin(c) * 0.2 + j * 0.3, r, g, b, alpha);
                }
            }
            case VOW -> {
                line(out, m, 0, 1, 0.65, 0, r, g, b, alpha); line(out, m, 0.65, 0, 0, -1, r, g, b, alpha);
                line(out, m, 0, -1, -0.65, 0, r, g, b, alpha); line(out, m, -0.65, 0, 0, 1, r, g, b, alpha);
                line(out, m, 0, -0.5, 0, 0.5, r, g, b, alpha);
            }
            case BONE -> {
                line(out, m, 0, -0.8, 0, 0.8, r, g, b, alpha);
                for (int i = -1; i <= 1; i++) line(out, m, -0.55, i * 0.5 + 0.1, 0.55, i * 0.5 - 0.1, r, g, b, alpha);
            }
        }
    }
    public static void line(VertexConsumer out, Matrix4f pose, double x1, double y1, double x2, double y2,
                             int r, int g, int b, int alpha) {
        Vec3 a = new Vec3(x1, y1, 0), c = new Vec3(x2, y2, 0);
        Vec3 width = c.subtract(a).cross(Z).normalize();
        WeaponGlowGeometry.strip(out, pose, a, c, width.scale(0.065), r, g, b, alpha * 2 / 3);
        WeaponGlowGeometry.strip(out, pose, a.add(0, 0, 0.002), c.add(0, 0, 0.002), width.scale(0.021), 244, 250, 255, alpha);
    }
}
