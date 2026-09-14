package com.stardew.craft.npc.data;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Central in-memory registry for NPC data-driven resources.
 * All data is packed into an immutable snapshot and atomically swapped
 * so readers never see a half-updated state.
 */
public final class NpcDataRegistry {

    /** Immutable snapshot of all NPC data. */
    private record Snapshot(
        Map<String, NpcCapabilityProfile> capabilities,
        Map<String, JsonObject> dialogues,
        Map<String, JsonObject> schedules,
        Map<String, JsonObject> tastes,
        Map<String, JsonObject> events,
        Set<String> locationMappings,
        Map<String, String> locationAliases,
        Map<String, NpcLocationAnchor> locationAnchors
    ) {
        static final Snapshot EMPTY = new Snapshot(
            Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(),
            Collections.emptyMap(), Collections.emptyMap(), Collections.emptySet(),
            Collections.emptyMap(), Collections.emptyMap()
        );
    }

    private static volatile Snapshot CURRENT = Snapshot.EMPTY;
    private static volatile Map<String,JsonObject> CLIENT_EVENTS = Map.of();
    public static Map<String,JsonObject> clientEvents() { return CLIENT_EVENTS; }
    public static void clearClientEvents() { CLIENT_EVENTS=Map.of(); }
    private static volatile long revision;

    public static long revision() { return revision; }

    private static final Gson GSON = new Gson();
    /** 缓存 events JSON（SoftReference），内存紧张时可被 GC 回收 */
    private static volatile java.lang.ref.SoftReference<String> CACHED_EVENTS_JSON_REF = new java.lang.ref.SoftReference<>(null);

    /** 获取缓存的 events JSON（服务端调用）。若 GC 回收则重新序列化 */
    public static String getCachedEventsJson() {
        String json = CACHED_EVENTS_JSON_REF.get();
        if (json != null) return json;
        json = rebuildEventsJson();
        CACHED_EVENTS_JSON_REF = new java.lang.ref.SoftReference<>(json);
        return json;
    }

    private static String rebuildEventsJson() {
        Map<String, JsonObject> events = CURRENT.events;
        if (events.isEmpty()) return "";
        com.google.gson.JsonObject root = new com.google.gson.JsonObject();
        for (Map.Entry<String, JsonObject> entry : events.entrySet()) {
            root.add(entry.getKey(), entry.getValue());
        }
        return GSON.toJson(root);
    }

    /** 从 JSON 字符串重放 events 数据（客户端调用） */
    public static void applyEventsFromJson(String json) {
        try {
            com.google.gson.JsonObject root = GSON.fromJson(json, com.google.gson.JsonObject.class);
            if (root == null) return;
            Map<String, JsonObject> events = new LinkedHashMap<>();
            for (Map.Entry<String, com.google.gson.JsonElement> entry : root.entrySet()) {
                if (entry.getValue().isJsonObject()) {
                    events.put(entry.getKey(), entry.getValue().getAsJsonObject());
                }
            }
            CLIENT_EVENTS=freezeJson(events);
        } catch (Exception e) {
            com.mojang.logging.LogUtils.getLogger().warn("Rejected client NPC event snapshot",e);
        }
    }

    private NpcDataRegistry() {
    }

    static void replaceAll(
            Map<String, NpcCapabilityProfile> capabilities,
            Map<String, JsonObject> dialogues,
            Map<String, JsonObject> schedules,
            Map<String, JsonObject> tastes,
            Map<String, JsonObject> events,
            Set<String> locationMappings,
            Map<String, String> locationAliases,
            Map<String, NpcLocationAnchor> locationAnchors
    ) {
        CURRENT = new Snapshot(
                Collections.unmodifiableMap(new LinkedHashMap<>(capabilities)),
                freezeJson(dialogues),
                freezeJson(schedules),
                freezeJson(tastes),
                freezeJson(events),
                Collections.unmodifiableSet(new LinkedHashSet<>(locationMappings)),
                Collections.unmodifiableMap(new LinkedHashMap<>(locationAliases)),
                Collections.unmodifiableMap(new LinkedHashMap<>(locationAnchors))
        );
        revision++;
        CACHED_EVENTS_JSON_REF = new java.lang.ref.SoftReference<>(null);
    }

    public static void replaceCapabilities(Map<String, NpcCapabilityProfile> capabilities) {
        Snapshot s = CURRENT;
        CURRENT = new Snapshot(Collections.unmodifiableMap(new LinkedHashMap<>(capabilities)),
            s.dialogues, s.schedules, s.tastes, s.events, s.locationMappings, s.locationAliases, s.locationAnchors);
        revision++;
    }

    public static void replaceDialogues(Map<String, JsonObject> dialogues) {
        Snapshot s = CURRENT;
        CURRENT = new Snapshot(s.capabilities, freezeJson(dialogues),
            s.schedules, s.tastes, s.events, s.locationMappings, s.locationAliases, s.locationAnchors);
        revision++;
    }

    public static void replaceSchedules(Map<String, JsonObject> schedules) {
        Snapshot s = CURRENT;
        CURRENT = new Snapshot(s.capabilities, s.dialogues, freezeJson(schedules),
            s.tastes, s.events, s.locationMappings, s.locationAliases, s.locationAnchors);
        revision++;
    }

    public static void replaceTastes(Map<String, JsonObject> tastes) {
        Snapshot s = CURRENT;
        CURRENT = new Snapshot(s.capabilities, s.dialogues, s.schedules,
            freezeJson(tastes), s.events, s.locationMappings, s.locationAliases, s.locationAnchors);
        revision++;
    }

    public static void replaceEvents(Map<String, JsonObject> events) {
        Snapshot s = CURRENT;
        Map<String, JsonObject> frozen = freezeJson(events);
        CURRENT = new Snapshot(s.capabilities, s.dialogues, s.schedules, s.tastes,
            frozen, s.locationMappings, s.locationAliases, s.locationAnchors);
        revision++;
        // 清除旧的缓存引用，下次 getCachedEventsJson() 时按需重建
        CACHED_EVENTS_JSON_REF = new java.lang.ref.SoftReference<>(null);
    }

    public static void replaceLocationMappings(Set<String> locations) {
        Snapshot s = CURRENT;
        CURRENT = new Snapshot(s.capabilities, s.dialogues, s.schedules, s.tastes, s.events,
            Collections.unmodifiableSet(new LinkedHashSet<>(locations)), s.locationAliases, s.locationAnchors);
        revision++;
    }

    public static void replaceLocationAliases(Map<String, String> aliases) {
        Snapshot s = CURRENT;
        CURRENT = new Snapshot(s.capabilities, s.dialogues, s.schedules, s.tastes, s.events,
            s.locationMappings, Collections.unmodifiableMap(new LinkedHashMap<>(aliases)), s.locationAnchors);
        revision++;
    }

    public static void replaceLocationAnchors(Map<String, NpcLocationAnchor> anchors) {
        Snapshot s = CURRENT;
        CURRENT = new Snapshot(s.capabilities, s.dialogues, s.schedules, s.tastes, s.events,
            s.locationMappings, s.locationAliases, Collections.unmodifiableMap(new LinkedHashMap<>(anchors)));
        revision++;
    }

    private static Map<String, JsonObject> freezeJson(Map<String, JsonObject> input) {
        Map<String, JsonObject> copy = new LinkedHashMap<>();
        input.forEach((key, value) -> copy.put(key, value.deepCopy()));
        return new JsonDocuments(copy);
    }

    /** Stable map identity for revision caches, with copy-on-read values instead of mutable snapshot access. */
    private static final class JsonDocuments extends java.util.AbstractMap<String,JsonObject> {
        private final Map<String,JsonObject> documents;
        JsonDocuments(Map<String,JsonObject> source) { documents=Map.copyOf(source); }
        @Override public JsonObject get(Object key) {
            var value=documents.get(key); return value==null?null:value.deepCopy();
        }
        @Override public boolean containsKey(Object key) { return documents.containsKey(key); }
        @Override public int size() { return documents.size(); }
        @Override public Set<Entry<String,JsonObject>> entrySet() {
            return new java.util.AbstractSet<>() {
                @Override public int size() { return documents.size(); }
                @Override public java.util.Iterator<Entry<String,JsonObject>> iterator() {
                    var entries=documents.entrySet().iterator();
                    return new java.util.Iterator<>() {
                        public boolean hasNext() { return entries.hasNext(); }
                        public Entry<String,JsonObject> next() {
                            var entry=entries.next();
                            return new SimpleImmutableEntry<>(entry.getKey(),entry.getValue().deepCopy());
                        }
                    };
                }
            };
        }
    }

    public static Map<String, NpcCapabilityProfile> capabilities() {
        return CURRENT.capabilities;
    }

    public static Map<String, JsonObject> dialogues() {
        return CURRENT.dialogues;
    }

    public static Map<String, JsonObject> schedules() {
        return CURRENT.schedules;
    }

    public static Map<String, JsonObject> tastes() {
        return CURRENT.tastes;
    }

    public static Map<String, JsonObject> events() {
        return CURRENT.events;
    }

    public static Set<String> locationMappings() {
        return CURRENT.locationMappings;
    }

    public static Map<String, String> locationAliases() {
        return CURRENT.locationAliases;
    }

    public static Map<String, NpcLocationAnchor> locationAnchors() {
        return CURRENT.locationAnchors;
    }
}
