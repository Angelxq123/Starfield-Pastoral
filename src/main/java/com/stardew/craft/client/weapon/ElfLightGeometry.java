package com.stardew.craft.client.weapon;

import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.List;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import static com.stardew.craft.client.weapon.WeaponGlowGeometry.*;

/** Untextured luminous volumes; fixed world axes remove billboard flips and arbitrary roll. */
public final class ElfLightGeometry {
    private ElfLightGeometry() {}
    public record Sample(Vec3 position, float fade) {}
    public static void core(VertexConsumer out, Matrix4f pose, Vec3 p) {
        // A tiny soft shell and a pale core, without a solid leaf silhouette.
        for (int layer = 0; layer < 2; layer++) {
            double r = layer == 0 ? 0.055 : 0.018;
            Vec3[] rim = {p.add(r,0,0), p.add(0,0,r), p.add(-r,0,0), p.add(0,0,-r)};
            for (int side : new int[]{-1, 1}) for (int i = 0; i < 4; i++) {
                Vec3 cap = p.add(0, side * r, 0);
                for (Vec3 v : new Vec3[]{rim[i], rim[(i+1)%4], cap, cap})
                    vertex(out, pose, v, layer == 0 ? 94 : 221, 245, layer == 0 ? 167 : 209, layer == 0 ? 48 : 190);
            }
        }
    }
    public static void trail(VertexConsumer out, Matrix4f pose, List<Sample> samples, double width) {
        // Crossed world-space ribbons retain depth even when viewed edge-on. No per-frame camera basis.
        for (int i = 1; i < samples.size(); i++) {
            Sample a = samples.get(i-1), b = samples.get(i);
            if (a.position.distanceToSqr(b.position) > 16) continue;
            for (int plane = 0; plane < 3; plane++) for (int layer = 0; layer < 2; layer++) {
                Vec3 axis = plane == 0 ? new Vec3(0,1,0) : plane == 1 ? new Vec3(1,0,0) : new Vec3(0,0,1);
                double w = width * (layer == 0 ? 1 : 0.22);
                int r = layer == 0 ? 67 : 210, g = layer == 0 ? 198 : 250, c = layer == 0 ? 145 : 199;
                int alpha = layer == 0 ? 48 : 125;
                vertex(out, pose, a.position.add(axis.scale(w*a.fade)), r,g,c, Math.round(alpha*a.fade*a.fade));
                vertex(out, pose, a.position.subtract(axis.scale(w*a.fade)), r,g,c, Math.round(alpha*a.fade*a.fade));
                vertex(out, pose, b.position.subtract(axis.scale(w*b.fade)), r,g,c, Math.round(alpha*b.fade*b.fade));
                vertex(out, pose, b.position.add(axis.scale(w*b.fade)), r,g,c, Math.round(alpha*b.fade*b.fade));
            }
        }
    }
}
