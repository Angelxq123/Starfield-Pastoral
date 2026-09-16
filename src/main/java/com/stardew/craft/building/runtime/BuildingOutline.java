package com.stardew.craft.building.runtime;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;

/** Exact axis-aligned edge subtraction: reservation edges win without inflated duplicate boxes. */
public final class BuildingOutline {
    public record Edge(Vec3 from, Vec3 to, int axis) {}
    private BuildingOutline() {}
    public static List<Edge> edges(AABB box) {
        List<Edge> result = new ArrayList<>();
        double[] min = {box.minX, box.minY, box.minZ}, max = {box.maxX, box.maxY, box.maxZ};
        for (int axis = 0; axis < 3; axis++) for (int a = 0; a < 2; a++) for (int b = 0; b < 2; b++) {
            double[] lo = min.clone(), hi = min.clone(); int one = (axis + 1) % 3, two = (axis + 2) % 3;
            lo[one] = hi[one] = a == 0 ? min[one] : max[one]; lo[two] = hi[two] = b == 0 ? min[two] : max[two];
            hi[axis] = max[axis];
            result.add(new Edge(new Vec3(lo[0], lo[1], lo[2]), new Vec3(hi[0], hi[1], hi[2]), axis));
        }
        return result;
    }
    private static double at(Vec3 point, int axis) { return axis == 0 ? point.x : axis == 1 ? point.y : point.z; }
    private static Edge segment(Edge edge, double from, double to) {
        Vec3 direction = edge.axis == 0 ? new Vec3(1, 0, 0) : edge.axis == 1 ? new Vec3(0, 1, 0) : new Vec3(0, 0, 1);
        return new Edge(edge.from.add(direction.scale(from - at(edge.from, edge.axis))),
                edge.from.add(direction.scale(to - at(edge.from, edge.axis))), edge.axis);
    }
    public static List<Edge> excluding(AABB box, AABB priority) {
        List<Edge> remaining = edges(box);
        for (Edge cut : edges(priority)) {
            List<Edge> next = new ArrayList<>();
            for (Edge edge : remaining) {
                if (edge.axis != cut.axis || at(edge.from, (edge.axis + 1) % 3) != at(cut.from, (edge.axis + 1) % 3)
                        || at(edge.from, (edge.axis + 2) % 3) != at(cut.from, (edge.axis + 2) % 3)) { next.add(edge); continue; }
                double start = at(edge.from, edge.axis), end = at(edge.to, edge.axis);
                double lo = Math.max(start, at(cut.from, cut.axis)), hi = Math.min(end, at(cut.to, cut.axis));
                if (lo >= hi) { next.add(edge); continue; }
                if (start < lo) next.add(segment(edge, start, lo));
                if (hi < end) next.add(segment(edge, hi, end));
            }
            remaining = next;
        }
        return List.copyOf(remaining);
    }
}
