package com.stardew.craft.client.weapon;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

public final class DwarfWeaponGeometry {
    private DwarfWeaponGeometry() {}
    static void shockContact(VertexConsumer out, Matrix4f pose, Vec3 center, Vec3 right, Vec3 up, Vec3 normal,
                             float size, float age, float fade, boolean edge, int r, int g, int b) {
        if (fade <= 0) return;
        // Short central compression and two broad chips; deliberately smaller than a direct sword hit.
        WeaponContactGeometry.blade(out, pose, center.subtract(right.scale(size * .38)), center.add(right.scale(size * .38)),
                normal, size * .08, fade, edge, r, g, b);
        for (int sign : new int[]{-1, 1}) {
            Vec3 p = center.add(right.scale(sign * size * (.18 + age * .025))).add(up.scale(size * (.10 - age * age * .005)));
            Vec3 axis = up.add(right.scale(sign * .3)).normalize().scale(size * .13);
            WeaponContactGeometry.blade(out, pose, p.subtract(axis), p.add(axis), normal, size * .045, fade, edge, r, g, b);
        }
    }
    public static void bladeRune(VertexConsumer out, Matrix4f pose, Vec3 base, Vec3 tip, float visibility, boolean dagger) {
        if (visibility <= 0) return;
        Vec3 along = tip.subtract(base).normalize(), side = new Vec3(-along.y, along.x, 0).normalize();
        for (int face : new int[]{-1, 1}) {
            Vec3 center = base.lerp(tip, dagger ? .62 : .48).add(0, 0, face * .037);
            for (int sign : new int[]{-1, 1}) {
                Vec3[] bracket = {center.add(along.scale(.07)).add(side.scale(sign * .024)),
                        center.add(side.scale(sign * .048)), center.subtract(along.scale(.07)).add(side.scale(sign * .024))};
                WeaponContactGeometry.ribbon(out, pose, bracket, new Vec3(0, 0, 1), .009, visibility * .7f, false,
                        dagger ? 147 : 236, dagger ? 216 : 191, dagger ? 215 : 109);
            }
        }
    }
    /** Axis-aligned square bands follow the existing fortress AABB, not a fictional circular hit area. */
    static Vec3[] perimeterSide(Vec3 center, double radius, int side) {
        Vec3[] points = new Vec3[13];
        for (int i = 0; i < points.length; i++) {
            double t = -.86 + i * 1.72 / 12;
            points[i] = center.add(switch (side) {
                case 0 -> new Vec3(t * radius, 0, -radius);
                case 1 -> new Vec3(radius, 0, t * radius);
                case 2 -> new Vec3(-t * radius, 0, radius);
                default -> new Vec3(-radius, 0, -t * radius);
            });
        }
        return points;
    }
    static float waveOpacity(float age, int band) {
        float t = age - band * 1.3f;
        return t < 0 || t >= 5 ? 0 : (float)Math.pow(1 - t / 5, 2);
    }
    static void groundStroke(VertexConsumer out, Matrix4f pose, Vec3[] path, float fade, boolean echo) {
        if (fade <= 0) return;
        if (path.length == 2) path = new Vec3[]{path[0], path[0].lerp(path[1], .5), path[1]};
        WeaponContactGeometry.ribbon(out, pose, path, new Vec3(0, 1, 0), echo ? .08 : .055, fade * .7f, false, 222, 164, 76);
    }
}
