package com.stardew.craft.client.weapon;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import static com.stardew.craft.client.weapon.WeaponGlowGeometry.vertex;

/** Tapered wind surfaces: a readable blade stroke and quiet open wisps, never gem debris. */
public final class GroveEffectGeometry {
    private GroveEffectGeometry() {}
    public static void slash(VertexConsumer out, Matrix4f pose, Vec3 center, Vec3 right, Vec3 up, Vec3 normal,
                             float size, float age, float fade, boolean forest, boolean edge) {
        int count = forest ? 24 : 18;
        Vec3[] path = new Vec3[count + 1];
        for (int i = 0; i <= count; i++)
            path[i] = cut(center, right, up, normal, size, i / (double) count, age, forest);
        WeaponContactGeometry.ribbon(out, pose, path, normal, size * (forest ? 0.12 : 0.075),
                fade, edge, edge ? 18 : 78, edge ? 49 : 191, edge ? 37 : 137);
    }
    private static Vec3 cut(Vec3 center, Vec3 right, Vec3 up, Vec3 normal, float size, double t, float age, boolean forest) {
        double x = -0.85 + 1.7 * t, y = -0.38 + 0.76 * t + (forest ? 0.36 : 0.08) * Math.sin(Math.PI * t);
        return center.add(right.scale(size * x)).add(up.scale(size * (y + age * 0.006)))
                .add(normal.scale(size * 0.045 * Math.sin(Math.PI * t)));
    }
    /** An open ascending arc; its thin luminous edge occupies a fraction of the green surface. */
    public static void wisp(VertexConsumer out, Matrix4f pose, Vec3 base, double angle, double radius,
                            double height, float fade) {
        for (int i = 0; i < 28; i++) {
            double a = i / 28.0, b = (i + 1) / 28.0;
            Vec3 p = wind(base, angle, radius, height, a), q = wind(base, angle, radius, height, b);
            Vec3 side = new Vec3(0, 1, 0);
            band(out, pose, p, q, side, 0.035, a, b, 62, 153, 116, Math.round(65 * fade));
            band(out, pose, p, q, side, 0.005, a, b, 186, 229, 170, Math.round(145 * fade));
        }
    }
    public static void sprig(VertexConsumer out, Matrix4f pose, int stacks, float fade) {
        for (int i = 0; i < 12; i++) {
            double a = i / 12.0, b = (i + 1) / 12.0;
            band(out, pose, new Vec3(0.018 * Math.sin(a * 2), -0.24 + a * 0.48, 0),
                    new Vec3(0.018 * Math.sin(b * 2), -0.24 + b * 0.48, 0), new Vec3(1, 0, 0),
                    0.006, a, b, 137, 206, 130, Math.round(150 * fade));
        }
        for (int i = 0; i < Math.min(10, stacks); i++) {
            double sign = i % 2 == 0 ? -1 : 1, y = -0.19 + (i / 2) * 0.085;
            for (int j = 0; j < 8; j++) {
                double a = j / 8.0, b = (j + 1) / 8.0;
                Vec3 p = new Vec3(sign * a * 0.105, y + a * 0.06 + Math.sin(a * Math.PI) * 0.02, 0);
                Vec3 q = new Vec3(sign * b * 0.105, y + b * 0.06 + Math.sin(b * Math.PI) * 0.02, 0);
                band(out, pose, p, q, new Vec3(0, 1, 0), 0.018, a, b, 171, 226, 136, Math.round(205 * fade));
            }
        }
    }
    private static Vec3 wind(Vec3 base, double angle, double radius, double height, double t) {
        double a = angle + t * 2.3;
        return base.add(Math.cos(a) * radius, height + t * 0.24, Math.sin(a) * radius);
    }
    private static void band(VertexConsumer out, Matrix4f pose, Vec3 a, Vec3 b, Vec3 side, double width,
                             double ta, double tb, int r, int g, int blue, int alpha) {
        band(out, pose, a, b, side, side, width, ta, tb, r, g, blue, alpha);
    }
    private static void band(VertexConsumer out, Matrix4f pose, Vec3 a, Vec3 b, Vec3 side, Vec3 endSide, double width,
                             double ta, double tb, int r, int g, int blue, int alpha) {
        double wa = Math.pow(Math.sin(Math.PI * ta), 1.3) * width, wb = Math.pow(Math.sin(Math.PI * tb), 1.3) * width;
        vertex(out, pose, a.subtract(side.scale(wa)), r, g, blue, 0);
        vertex(out, pose, a.add(side.scale(wa)), r, g, blue, Math.round(alpha * (float) Math.sin(Math.PI * ta)));
        vertex(out, pose, b.add(endSide.scale(wb)), r, g, blue, Math.round(alpha * (float) Math.sin(Math.PI * tb)));
        vertex(out, pose, b.subtract(endSide.scale(wb)), r, g, blue, 0);
    }
}
