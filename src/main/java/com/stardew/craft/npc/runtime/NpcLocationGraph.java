package com.stardew.craft.npc.runtime;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.stardew.craft.npc.data.NpcDataRegistry;
import com.stardew.craft.npc.data.NpcLocationAnchor;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;

/**
 * BFS-based location connectivity graph.
 * <p>
 * Data is loaded from {@code npc/events/location_graph.json}. Each edge describes an
 * outdoor walk or walk+warp transition between two locations. The graph is bidirectional.
 * <p>
 * When {@link NpcRoutePlanner} cannot find a profile route for an NPC, this graph
 * automatically stitches a multi-hop route from current location to destination.
 */
public final class NpcLocationGraph {

    /** One directed edge in the graph. */
    record Edge(String from, String to, String viaOutdoor, String viaIndoor, String mode, boolean reversed) {
    }

    /** BFS result: ordered list of edges from source to destination. */
    record GraphRoute(List<Edge> edges) {
        static final GraphRoute EMPTY = new GraphRoute(Collections.emptyList());
    }

    private static volatile Map<String, List<Edge>> adjacency = Collections.emptyMap();

    private NpcLocationGraph() {
    }

    // ---- loading ----

    public static void reload() {
        JsonObject root = NpcDataRegistry.events().get("location_graph");
        if (root == null || !root.has("edges") || !root.get("edges").isJsonArray()) {
            adjacency = Collections.emptyMap();
            return;
        }

        Map<String, List<Edge>> adj = new HashMap<>();
        JsonArray arr = root.getAsJsonArray("edges");
        for (JsonElement el : arr) {
            if (!el.isJsonObject()) continue;
            JsonObject obj = el.getAsJsonObject();
            String from = str(obj, "from");
            String to = str(obj, "to");
            if (from.isEmpty() || to.isEmpty()) continue;

            String viaOutdoor = str(obj, "via_outdoor");
            String viaIndoor = str(obj, "via_indoor");
            String reverseViaOutdoor = str(obj, "reverse_via_outdoor");
            String reverseViaIndoor = str(obj, "reverse_via_indoor");
            String mode = str(obj, "mode");
            if (mode.isEmpty()) mode = "walk";

            Edge forward = new Edge(from, to, viaOutdoor, viaIndoor, mode, false);
            Edge reverse = reverseViaOutdoor.isEmpty() && reverseViaIndoor.isEmpty()
                ? new Edge(to, from, viaOutdoor, viaIndoor, mode, true)
                : new Edge(to, from, reverseViaOutdoor, reverseViaIndoor, mode, false);

            adj.computeIfAbsent(from, k -> new ArrayList<>()).add(forward);
            adj.computeIfAbsent(to, k -> new ArrayList<>()).add(reverse);
        }

        adjacency = Collections.unmodifiableMap(adj);
    }

    // ---- BFS shortest-path ----

    static GraphRoute findRoute(String fromLocation, String toLocation) {
        if (fromLocation == null || toLocation == null) return GraphRoute.EMPTY;
        String src = canonical(fromLocation);
        String dst = canonical(toLocation);
        if (src.equals(dst) || src.isEmpty() || dst.isEmpty()) return GraphRoute.EMPTY;
        if (adjacency.isEmpty()) return GraphRoute.EMPTY;

        // BFS
        Queue<String> queue = new ArrayDeque<>();
        Map<String, Edge> cameFrom = new HashMap<>();
        queue.add(src);
        cameFrom.put(src, null);

        while (!queue.isEmpty()) {
            String current = queue.poll();
            if (current.equals(dst)) break;
            List<Edge> neighbors = adjacency.getOrDefault(current, Collections.emptyList());
            for (Edge edge : neighbors) {
                String next = edge.from.equals(current) ? edge.to : edge.from;
                if (cameFrom.containsKey(next)) continue;
                cameFrom.put(next, edge);
                queue.add(next);
            }
        }

        if (!cameFrom.containsKey(dst)) return GraphRoute.EMPTY;

        // Reconstruct path
        List<Edge> path = new ArrayList<>();
        String cursor = dst;
        while (!cursor.equals(src)) {
            Edge edge = cameFrom.get(cursor);
            if (edge == null) break;
            path.add(edge);
            cursor = edge.from.equals(cursor) ? edge.to : edge.from;
        }
        Collections.reverse(path);
        return new GraphRoute(path);
    }

    // ---- route step conversion ----

    /**
     * Convert a graph route into a sequence of {@link NpcRoutePlanner.NpcRouteStep}s.
     * Each edge is interpreted in its directed BFS direction. Outdoor -> indoor
     * means walk to the outdoor door and WARP to the indoor entry; indoor ->
     * outdoor means walk to that interior's exit and WARP to its outdoor exit.
     */
    static List<NpcRoutePlanner.NpcRouteStep> toRouteSteps(GraphRoute route, Vec3 finalTarget) {
        return toRouteSteps(null,route,finalTarget);
    }

    static List<NpcRoutePlanner.NpcRouteStep> toRouteSteps(net.minecraft.server.level.ServerLevel level,GraphRoute route,Vec3 finalTarget) {
        if (route == null || route.edges.isEmpty()) return Collections.emptyList();

        List<NpcRoutePlanner.NpcRouteStep> steps = new ArrayList<>();

        for (int i = 0; i < route.edges.size(); i++) {
            Edge edge = route.edges.get(i);
            boolean isLast = (i == route.edges.size() - 1);
            NpcLocationAnchor fromAnchor = NpcDataRegistry.locationAnchors().get(edge.from);
            NpcLocationAnchor toAnchor = NpcDataRegistry.locationAnchors().get(edge.to);
            boolean fromIndoor = fromAnchor != null && fromAnchor.indoor();
            boolean toIndoor = toAnchor != null && toAnchor.indoor();

            if (!fromIndoor && toIndoor) {
                Vec3 outdoor = !edge.viaOutdoor.isEmpty()
                    ? NpcRoutePlanner.pointFromConfig(level,edge.viaOutdoor, null)
                    : NpcRoutePlanner.outdoorDoorForLocation(level,edge.to);
                Vec3 indoor = !edge.viaIndoor.isEmpty()
                    ? NpcRoutePlanner.pointFromConfig(level,edge.viaIndoor, null)
                    : NpcRoutePlanner.indoorEntryForLocation(level,edge.to);
                if (outdoor == null || indoor == null) return Collections.emptyList();
                if (outdoor != null) {
                    steps.add(NpcRoutePlanner.NpcRouteStep.walk("graph_outdoor_" + edge.to, outdoor));
                }
                if (indoor != null) {
                    steps.add(NpcRoutePlanner.NpcRouteStep.warp("graph_indoor_" + edge.to, indoor));
                }
            } else if (fromIndoor && !toIndoor) {
                Vec3 indoorExit = NpcRoutePlanner.indoorExitForLocation(level,edge.from);
                Vec3 outdoorExit = NpcRoutePlanner.outdoorExitForLocation(level,edge.from);
                if (indoorExit == null || outdoorExit == null) return Collections.emptyList();
                if (indoorExit != null) {
                    steps.add(NpcRoutePlanner.NpcRouteStep.walk("graph_indoor_exit_" + edge.from, indoorExit));
                }
                if (outdoorExit != null) {
                    steps.add(NpcRoutePlanner.NpcRouteStep.warp("graph_outdoor_exit_" + edge.from, outdoorExit));
                }
            } else if (!fromIndoor && !toIndoor && isWarpMode(edge.mode)) {
                String walkPointId = edge.reversed ? edge.viaIndoor : edge.viaOutdoor;
                String warpPointId = edge.reversed ? edge.viaOutdoor : edge.viaIndoor;
                Vec3 walkPoint = NpcRoutePlanner.pointFromConfig(level,walkPointId, null);
                Vec3 warpPoint = NpcRoutePlanner.pointFromConfig(level,warpPointId, null);
                if (walkPoint == null || warpPoint == null) return Collections.emptyList();
                if (walkPoint != null) {
                    steps.add(NpcRoutePlanner.NpcRouteStep.walk("graph_outdoor_warp_depart_" + edge.to, walkPoint));
                }
                if (warpPoint != null) {
                    steps.add(NpcRoutePlanner.NpcRouteStep.warp("graph_outdoor_warp_arrive_" + edge.to, warpPoint));
                }
            } else if (!isLast && !edge.viaOutdoor.isEmpty()) {
                Vec3 outdoor = NpcRoutePlanner.pointFromConfig(level,edge.viaOutdoor, null);
                if (outdoor == null) return Collections.emptyList();
                if (outdoor != null) {
                    steps.add(NpcRoutePlanner.NpcRouteStep.walk("graph_hop_" + edge.to, outdoor));
                }
            }
        }

        // Final target
        if (finalTarget != null) {
            steps.add(NpcRoutePlanner.NpcRouteStep.walk("graph_final_target", finalTarget));
        }

        return steps;
    }

    private static boolean isWarpMode(String mode) {
        return "warp".equalsIgnoreCase(mode) || "walk_warp".equalsIgnoreCase(mode);
    }

    /** Check if the graph has any edges loaded. */
    static boolean isAvailable() {
        return !adjacency.isEmpty();
    }

    static String outdoorNeighborFor(String location) {
        String canonicalLocation = canonical(location);
        if (canonicalLocation.isEmpty() || adjacency.isEmpty()) {
            return "";
        }
        for (Edge edge : adjacency.getOrDefault(canonicalLocation, Collections.emptyList())) {
            String candidate = edge.to.equals(canonicalLocation) ? edge.from : edge.to;
            NpcLocationAnchor anchor = NpcDataRegistry.locationAnchors().get(candidate);
            if (anchor == null || !anchor.indoor()) {
                return candidate;
            }
        }
        return "";
    }

    private static String canonical(String loc) {
        if (loc == null) return "";
        String raw = loc.trim().toLowerCase(Locale.ROOT);
        return NpcDataRegistry.locationAliases().getOrDefault(raw, raw);
    }

    private static String str(JsonObject obj, String key) {
        if (!obj.has(key) || !obj.get(key).isJsonPrimitive()) return "";
        String v = obj.get(key).getAsString();
        return v == null ? "" : v.trim().toLowerCase(Locale.ROOT);
    }
}
