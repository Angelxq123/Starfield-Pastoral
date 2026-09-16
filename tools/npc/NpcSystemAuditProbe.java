package com.stardew.craft.npc.runtime;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.stardew.craft.npc.data.NpcContentFilter;
import com.stardew.craft.npc.data.NpcDataManager;
import com.stardew.craft.npc.data.NpcDataRegistry;
import com.stardew.craft.npc.data.NpcLocationAnchor;
import com.stardew.craft.api.v1.world.StardewWorldAnchor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Read-only audit of production methods in a disposable JVM, not a correctness gate. */
public final class NpcSystemAuditProbe {
    public static void main(String[] args) throws Exception {
        Path resources = Path.of(args[0]);
        Map<String, Object> observations = new LinkedHashMap<>();

        Method signature = method(NpcCentralMovementService.class, "buildPlanSignature",
                NpcRuntimeState.class, NpcRoutePlanner.NpcRouteContext.class);
        Method changed = method(NpcCentralMovementService.class, "markAndCheckScheduleNodeChange",
                String.class, NpcRuntimeState.class);
        NpcRuntimeState state = new NpcRuntimeState("audit_probe");
        state.setNamedPointId("point_a");
        var first = NpcRoutePlanner.NpcRouteContext.ready("town", List.of(
                NpcRoutePlanner.NpcRouteStep.walk("point_a", new Vec3(1, 64, 1))));
        String original = (String) signature.invoke(null, state, first);
        changed.invoke(null, state.npcId(), state);
        state.setNamedPointId("point_b");
        var second = NpcRoutePlanner.NpcRouteContext.ready("town", List.of(
                NpcRoutePlanner.NpcRouteStep.walk("point_b", new Vec3(100, 64, 100))));
        observations.put("different_named_target_same_plan_signature",
                original.equals(signature.invoke(null, state, second)));
        observations.put("different_named_target_detected_as_node_change",
                changed.invoke(null, state.npcId(), state));

        JsonObject unknownCondition = JsonParser.parseString(
                "{\"_condition\":\"UNRECOGNIZED_FLAG must_be_true\"}").getAsJsonObject();
        observations.put("unknown_schedule_condition_allowed", method(NpcScheduleRuntimeService.class,
                "scheduleConditionPasses", ServerLevel.class, JsonObject.class, java.util.UUID.class)
                .invoke(null, null, unknownCondition, null));

        Map<String, JsonObject> events = new HashMap<>();
        JsonObject points = JsonParser.parseString("""
                {"points":{"landing":{"x":10,"y":39,"z":10}}}
                """).getAsJsonObject();
        events.put("npc_route_points", points);
        events.put("location_graph", JsonParser.parseString("""
                {"edges":[{"from":"town","to":"room","via_outdoor":"missing_door",
                "via_indoor":"landing","mode":"walk_warp"}]}
                """).getAsJsonObject());
        NpcDataRegistry.replaceEvents(events);
        NpcDataRegistry.replaceLocationAnchors(Map.of("room",
                new NpcLocationAnchor(0, 39, 0, true, "", false, false, "", "", "")));
        NpcLocationGraph.reload();
        var route = NpcLocationGraph.toRouteSteps(NpcLocationGraph.findRoute("town", "room"),
                new Vec3(11, 39, 11));
        observations.put("graph_with_missing_departure_steps",
                route.stream().map(step -> step.mode + ":" + step.pointId).toList());

        points.addProperty("changed_after_publication", true);
        observations.put("published_json_mutable_through_original_reference",
                NpcDataRegistry.events().get("npc_route_points").has("changed_after_publication"));

        // Use the actual mapping parser, including aliases and anchor names, before filtering.
        Set<String> locations = new HashSet<>();
        Map<String, String> aliases = new HashMap<>();
        Map<String, NpcLocationAnchor> anchors = new HashMap<>();
        Method mappings = method(NpcDataManager.ReloadListener.class, "parseLocationMappings",
                JsonObject.class, Set.class, Map.class, Map.class);
        try (var files = Files.list(resources.resolve("location_mappings"))) {
            for (Path file : files.filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
                mappings.invoke(null, read(file), locations, aliases, anchors);
            }
        }
        List<String> removed = new ArrayList<>();
        int documents = 0, nodeCount = 0;
        Set<String> namedIds = new HashSet<>();
        try (var files = Files.list(resources.resolve("schedules"))) {
            for (Path file : files.filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
                documents++;
                JsonObject input = read(file);
                JsonObject filtered = NpcContentFilter.filterSchedules(input, locations);
                for (var day : input.entrySet()) {
                    if (!day.getValue().isJsonObject()) continue;
                    for (var node : day.getValue().getAsJsonObject().entrySet()) {
                        if (!node.getKey().matches("[0-9]+") || !node.getValue().isJsonPrimitive()) continue;
                        nodeCount++;
                        for (String part : node.getValue().getAsString().split("\\s+")) {
                            if (part.startsWith("@")) namedIds.add(part.substring(1));
                        }
                        if (!filtered.getAsJsonObject(day.getKey()).has(node.getKey())) {
                            removed.add(file.getFileName() + "/" + day.getKey() + "/" + node.getKey()
                                    + " -> " + node.getValue().getAsString());
                        }
                    }
                }
            }
        }
        var shippedPoints = read(resources.resolve("events/npc_route_points.json")).getAsJsonObject("points");
        observations.put("schedule_documents", documents);
        observations.put("schedule_time_nodes", nodeCount);
        observations.put("named_target_ids", namedIds.size());
        observations.put("legacy_route_points", shippedPoints.size());
        observations.put("named_targets_absent_from_legacy_points",
                namedIds.stream().filter(id -> !shippedPoints.has(id)).sorted().toList());
        observations.put("time_nodes_removed_by_production_filter", removed);
        Map<ResourceLocation, StardewWorldAnchor> projected = projectAnchors(
                read(resources.resolve("events/npc_route_points.json")));
        var chair = projected.get(ResourceLocation.fromNamespaceAndPath("stardewcraft", "sam_schedule_05"));
        observations.put("sam_chair_registered_anchor",
                chair == null ? "missing" : List.of(chair.position().x, chair.position().y, chair.position().z));
        String report = new GsonBuilder().setPrettyPrinting().create().toJson(observations);
        Path output = Path.of(args[1]);
        Files.createDirectories(output.getParent());
        Files.writeString(output, report + "\n");
        System.out.println(report);
    }

    private static Method method(Class<?> type, String name, Class<?>... parameters) throws Exception {
        Method result = type.getDeclaredMethod(name, parameters);
        result.setAccessible(true);
        return result;
    }

    private static JsonObject read(Path file) throws Exception {
        return JsonParser.parseString(Files.readString(file)).getAsJsonObject();
    }

    @SuppressWarnings("unchecked")
    private static Map<ResourceLocation, StardewWorldAnchor> projectAnchors(JsonObject points) throws Exception {
        return (Map<ResourceLocation, StardewWorldAnchor>) method(NpcDataManager.ReloadListener.class,
                "legacyRouteAnchors", Map.class).invoke(null, Map.of("npc_route_points", points));
    }
}
