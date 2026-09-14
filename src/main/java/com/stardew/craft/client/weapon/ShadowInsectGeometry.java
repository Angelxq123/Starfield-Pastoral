package com.stardew.craft.client.weapon;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** Tapered light surfaces, with restrained depth and no camera-driven rotation. */
public final class ShadowInsectGeometry {
    private ShadowInsectGeometry() {}

    static void contact(VertexConsumer out, Matrix4f pose, Vec3 center, Vec3 right, Vec3 up, Vec3 normal,
                        WeaponTargetImpactClient.Style style, float age, float fade, boolean edge, int r, int g, int b) {
        if (fade <= 0) return;
        double size = style.size;
        if (style == WeaponTargetImpactClient.Style.SHADOW_FINISH) {
            // Root contact already supplies the cross. The confirmed bonus separates two short echoes.
            for (int sign : new int[]{-1, 1}) {
                Vec3 axis = right.add(up.scale(.55)).normalize();
                Vec3 offset = up.scale(sign * (.08 + Math.min(age, 4) * .035) * size);
                Vec3 p = center.add(offset).add(normal.scale(.025));
                WeaponContactGeometry.blade(out, pose, p.subtract(axis.scale(.42 * size)), p.add(axis.scale(.42 * size)),
                        normal, .055 * size, fade * .7f, edge, r, g, b);
            }
            return;
        }
        // Paired curved jaws. The narrow amber cut stays distinct from the broader jade outer edge.
        boolean dash = style == WeaponTargetImpactClient.Style.INSECT_DASH;
        for (int sign : new int[]{-1, 1}) {
            Vec3[] arc = new Vec3[17];
            double open = Math.min(age, 4) * .018;
            for (int i = 0; i < arc.length; i++) {
                double t = i / 16.0;
                double x = sign * (.06 + Math.sin(t * Math.PI) * (.24 + open));
                double y = .55 - t * 1.05;
                arc[i] = center.add(right.scale((x + y * .28) * size)).add(up.scale(y * size))
                        .add(normal.scale(Math.sin(t * Math.PI) * .035));
            }
            WeaponContactGeometry.ribbon(out, pose, arc, normal, (dash ? .075 : .06) * size, fade, edge,
                    edge ? r : sign > 0 ? 236 : 112, edge ? g : sign > 0 ? 213 : 202, edge ? b : sign > 0 ? 137 : 159);
        }
    }

    public static void eyeLights(VertexConsumer out, Matrix4f pose, Vec3 base, Vec3 tip, float remaining, float elapsed) {
        if (remaining <= 0) return;
        Vec3 along = tip.subtract(base).normalize(), side = new Vec3(-along.y, along.x, 0).normalize();
        float fade = Math.min(1, remaining / 5) * Math.min(1, Math.max(0, elapsed) / 2);
        for (int face : new int[]{-1, 1}) for (int row = 0; row < 3; row++) for (int sign : new int[]{-1, 1}) {
            Vec3 p = base.lerp(tip, .56 + row * .105).add(side.scale(sign * .023)).add(0, 0, face * .037);
            WeaponContactGeometry.blade(out, pose, p.subtract(along.scale(.014)), p.add(along.scale(.014)),
                    new Vec3(0, 0, 1), .008, fade * .8f, false, row == 1 ? 229 : 118, 216, row == 1 ? 151 : 176);
        }
    }

    /** World-space wings stretch only between observed positions; callers pass camera-relative coordinates. */
    static void wake(VertexConsumer out, Matrix4f pose, Vec3 from, Vec3 to, float fade) {
        Vec3 forward = to.subtract(from);
        double length = forward.length();
        if (length < .035 || length > 5 || fade <= 0) return;
        forward = forward.scale(1 / length);
        Vec3 side = forward.cross(new Vec3(0, 1, 0)).normalize();
        if (side.lengthSqr() < 1e-6) return;
        Vec3 normal = side.cross(forward).normalize();
        for (int sign : new int[]{-1, 1}) {
            Vec3[] wing = new Vec3[17];
            for (int i = 0; i < wing.length; i++) {
                double t = i / 16.0, bow = Math.sin(t * Math.PI);
                wing[i] = from.lerp(to, t).add(side.scale(sign * (.18 + bow * .18))).add(0, .58 + bow * .045, 0);
            }
            WeaponContactGeometry.ribbon(out, pose, wing, normal, .07, fade * .65f, false, 109, 195, 161);
            // Slightly raised gold vein: a second depth plane, not a second large glow sheet.
            for (int i = 0; i < wing.length; i++) wing[i] = wing[i].add(normal.scale(.016));
            WeaponContactGeometry.ribbon(out, pose, wing, normal, .017, fade * .85f, false, 239, 218, 149);
        }
    }
}
