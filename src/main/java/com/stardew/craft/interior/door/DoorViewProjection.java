package com.stardew.craft.interior.door;

import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/**
 * Keeps development builds compatible with an already-loaded portal renderer.
 * The current renderer no longer calls this helper.
 */
public final class DoorViewProjection {
    private DoorViewProjection() {
    }

    public record ScreenRect(int x, int y, int width, int height) {
    }

    public static ScreenRect screenBounds(Vec3 center, double width, Vec3 camera, Matrix4f view,
            Matrix4f projection, int screenWidth, int screenHeight) {
        float minX = screenWidth;
        float minY = screenHeight;
        float maxX = 0;
        float maxY = 0;
        Matrix4f matrix = new Matrix4f(projection).mul(view);

        for (int x : new int[] {-1, 1}) {
            for (int y : new int[] {-1, 1}) {
                Vector4f point = new Vector4f(
                        (float) (center.x + x * width * 0.5 - camera.x),
                        (float) (center.y + y - camera.y),
                        (float) (center.z - camera.z),
                        1.0F).mul(matrix);
                if (point.w <= 0.001F) {
                    return new ScreenRect(0, 0, screenWidth, screenHeight);
                }

                float screenX = (point.x / point.w + 1.0F) * 0.5F * screenWidth;
                float screenY = (point.y / point.w + 1.0F) * 0.5F * screenHeight;
                minX = Math.min(minX, screenX);
                minY = Math.min(minY, screenY);
                maxX = Math.max(maxX, screenX);
                maxY = Math.max(maxY, screenY);
            }
        }

        int left = Math.max(0, (int) Math.floor(minX) - 2);
        int bottom = Math.max(0, (int) Math.floor(minY) - 2);
        int right = Math.min(screenWidth, (int) Math.ceil(maxX) + 2);
        int top = Math.min(screenHeight, (int) Math.ceil(maxY) + 2);
        return right > left && top > bottom
                ? new ScreenRect(left, bottom, right - left, top - bottom)
                : null;
    }
}
