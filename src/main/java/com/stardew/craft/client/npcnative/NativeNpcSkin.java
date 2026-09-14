package com.stardew.craft.client.npcnative;

import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Normalized linear blend skinning of continuous joint rows, as in the fishing rig. */
public final class NativeNpcSkin {
    private final NativeNpcModel model;
    private final float[][][] surface;
    private final int[] skinned;
    private final Vector3f upper = new Vector3f();
    private final Vector3f lower = new Vector3f();

    public NativeNpcSkin(NativeNpcModel model) {
        this.model = model;
        surface = new float[model.quads().size()][][];
        skinned = java.util.stream.IntStream.range(0, surface.length)
                .filter(i -> model.quads().get(i).skin() != null).toArray();
        for (int i : skinned) surface[i] = new float[4][3];
    }

    public float[][][] update(Matrix4f[] matrices, float[][][] cloth) {
        if (skinned.length == 0) return cloth;
        if (cloth != null) for (int i=0;i<surface.length;i++)
            if (model.quads().get(i).skin() == null) surface[i] = cloth[i];
        for (int qi : skinned) {
            var quad = model.quads().get(qi);
            var binding = quad.skin();
            for (int vi=0;vi<4;vi++) {
                var v = quad.vertices()[vi];
                matrices[binding.upper()].transformPosition(upper.set(v[0],v[1],v[2]));
                matrices[binding.lower()].transformPosition(lower.set(v[0],v[1],v[2]));
                upper.lerp(lower,binding.weights()[vi]);
                surface[qi][vi][0]=upper.x;
                surface[qi][vi][1]=upper.y;
                surface[qi][vi][2]=upper.z;
            }
        }
        return surface;
    }
}
