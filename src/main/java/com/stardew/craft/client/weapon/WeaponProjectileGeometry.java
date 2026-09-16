package com.stardew.craft.client.weapon;

import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix4f;

/** Small solid silhouettes replace camera-facing projectile pictures. */
public final class WeaponProjectileGeometry {
    public enum Shape { ANCHOR, BILLET }
    private WeaponProjectileGeometry() {}
    public static void render(VertexConsumer out, Matrix4f pose, Shape shape) {
        switch (shape) {
            case ANCHOR -> {
                box(out, pose, 0.045f, 0.42f, 0.045f, 78, 216, 237);
                box(out, new Matrix4f(pose).translate(0, 0.15f, 0), 0.22f, 0.035f, 0.04f, 194, 251, 255);
                WeaponEffectMesh.renderRing3D(out, new Matrix4f(pose).translate(0, 0.48f, 0).rotateX((float) Math.PI / 2),
                        0xF000F0, 186, 247, 255, 230, 0.07f, 0.095f, 0.025f, 16, 0);
                for (int sign : new int[]{-1, 1}) {
                    Matrix4f hook = new Matrix4f(pose).translate(sign * 0.15f, -0.28f, 0).rotateZ(sign * -0.8f);
                    box(out, hook, 0.045f, 0.22f, 0.04f, 94, 228, 242);
                }
            }
            case BILLET -> {
                box(out, pose, 0.19f, 0.34f, 0.12f, 148, 65, 33);
                box(out, new Matrix4f(pose).translate(0, 0, 0.125f), 0.13f, 0.27f, 0.012f, 255, 174, 72);
            }
        }
    }
    private static void box(VertexConsumer out, Matrix4f pose, float x, float y, float z, int r, int g, int b) {
        WeaponEffectMesh.renderBox(out, pose, 0xF000F0, r, g, b, 240, x, y, z, 0, 0);
    }
}
