package com.stardew.craft.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Shared geometry coordinates for baked models and server-side collision. Units are model pixels. */
public final class ModelGeometry {
    private ModelGeometry() {}

    public static Matrix4f transform(JsonObject part) {
        Matrix4f matrix = new Matrix4f();
        if (part.has("transform")) {
            JsonArray values = part.getAsJsonArray("transform");
            if (values.size() != 16) throw new IllegalArgumentException("transform must contain 16 column-major values");
            float[] data = new float[16];
            for (int i = 0; i < 16; i++) {
                data[i] = values.get(i).getAsFloat();
                if (!Float.isFinite(data[i])) throw new IllegalArgumentException("Non-finite transform");
            }
            matrix.set(data);
        }
        JsonObject rotation = part.getAsJsonObject("rotation");
        if (rotation != null) {
            Vector3f pivot = vector(rotation.getAsJsonArray("origin"));
            float angle = (float) Math.toRadians(rotation.get("angle").getAsDouble());
            String axis = rotation.get("axis").getAsString();
            matrix.translate(pivot);
            switch (axis) {
                case "x" -> matrix.rotateX(angle);
                case "y" -> matrix.rotateY(angle);
                case "z" -> matrix.rotateZ(angle);
                default -> throw new IllegalArgumentException("Invalid rotation axis " + axis);
            }
            if (rotation.has("rescale") && rotation.get("rescale").getAsBoolean()) {
                float scale = 1.0F / (float) Math.cos(angle);
                matrix.scale(axis.equals("x") ? 1 : scale, axis.equals("y") ? 1 : scale, axis.equals("z") ? 1 : scale);
            }
            matrix.translate(-pivot.x, -pivot.y, -pivot.z);
        }
        return matrix;
    }

    public static Vector3f vector(JsonArray values) {
        if (values == null || values.size() != 3) throw new IllegalArgumentException("Expected three coordinates");
        Vector3f out = new Vector3f(values.get(0).getAsFloat(), values.get(1).getAsFloat(), values.get(2).getAsFloat());
        if (!out.isFinite()) throw new IllegalArgumentException("Non-finite coordinates");
        return out;
    }

    public static VoxelShape shape(JsonArray elements, boolean voxel) {
        VoxelShape result = Shapes.empty();
        if (elements == null) return result;
        for (var value : elements) {
            JsonObject element = value.getAsJsonObject();
            Vector3f from = vector(element.getAsJsonArray("from"));
            Vector3f to = vector(element.getAsJsonArray("to"));
            // Plane faces still need a thin hittable surface.
            to.max(new Vector3f(from).add(0.001F, 0.001F, 0.001F));
            Matrix4f matrix = transform(element);
            Vector3f min = new Vector3f(Float.POSITIVE_INFINITY);
            Vector3f max = new Vector3f(Float.NEGATIVE_INFINITY);
            for (int i = 0; i < 8; i++) {
                Vector3f corner = matrix.transformPosition(new Vector3f(
                    (i & 1) == 0 ? from.x : to.x, (i & 2) == 0 ? from.y : to.y, (i & 4) == 0 ? from.z : to.z));
                min.min(corner);
                max.max(corner);
            }
            if (!voxel) {
                // Keep the union unoptimized while accumulating parts; simplify once below.
                result = Shapes.joinUnoptimized(result, Block.box(min.x, min.y, min.z, max.x, max.y, max.z), BooleanOp.OR);
                continue;
            }
            long volume = (long) Math.ceil(max.x - min.x + 1) * (long) Math.ceil(max.y - min.y + 1) * (long) Math.ceil(max.z - min.z + 1);
            if (volume > 1_000_000 || Math.abs(matrix.determinant()) < 1.0E-8F) {
                throw new IllegalArgumentException("Model exceeds voxel budget or has singular transform; use AABB/custom collision");
            }
            Matrix4f inverse = new Matrix4f(matrix).invert();
            // Conservative cell/OBB overlap: never leave a subpixel part unhittable.
            Vector3f half = new Vector3f(
                Math.abs(inverse.m00()) + Math.abs(inverse.m10()) + Math.abs(inverse.m20()),
                Math.abs(inverse.m01()) + Math.abs(inverse.m11()) + Math.abs(inverse.m21()),
                Math.abs(inverse.m02()) + Math.abs(inverse.m12()) + Math.abs(inverse.m22())).mul(0.5F);
            for (int x = (int) Math.floor(min.x + 1.0E-5); x < Math.ceil(max.x - 1.0E-5); x++) {
                for (int y = (int) Math.floor(min.y + 1.0E-5); y < Math.ceil(max.y - 1.0E-5); y++) {
                    int runStart = Integer.MIN_VALUE;
                    int zEnd = (int) Math.ceil(max.z - 1.0E-5);
                    for (int z = (int) Math.floor(min.z + 1.0E-5); z <= zEnd; z++) {
                        Vector3f center = inverse.transformPosition(new Vector3f(x + 0.5F, y + 0.5F, z + 0.5F));
                        boolean inside = z < zEnd && center.x + half.x > from.x + 1.0E-5 && center.x - half.x < to.x - 1.0E-5
                            && center.y + half.y > from.y + 1.0E-5 && center.y - half.y < to.y - 1.0E-5
                            && center.z + half.z > from.z + 1.0E-5 && center.z - half.z < to.z - 1.0E-5;
                        if (inside && runStart == Integer.MIN_VALUE) runStart = z;
                        if (!inside && runStart != Integer.MIN_VALUE) {
                            result = Shapes.joinUnoptimized(result, Block.box(x, y, runStart, x + 1, y + 1, z), BooleanOp.OR);
                            runStart = Integer.MIN_VALUE;
                        }
                    }
                }
            }
        }
        return result.optimize();
    }
}
