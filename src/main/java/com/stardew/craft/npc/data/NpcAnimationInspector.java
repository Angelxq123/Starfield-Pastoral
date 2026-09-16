package com.stardew.craft.npc.data;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Checks NPC animation assets to determine whether walk animation exists.
 */
public final class NpcAnimationInspector {
    private static final Map<String, Boolean> NATIVE_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> WALK_ANIM_CACHE = new ConcurrentHashMap<>();

    private NpcAnimationInspector() {
    }

    public static boolean hasWalkAnimation(String npcId) {
        if (npcId == null || npcId.isBlank()) {
            return false;
        }
        String key = NpcModelOwnership.normalize(npcId);
        Boolean cached = WALK_ANIM_CACHE.get(key);
        if (cached != null) return cached;

        boolean available = hasNativeWalk(key) || (!NpcModelOwnership.requiresNative(key) && hasLegacyWalk(key));
        WALK_ANIM_CACHE.put(key, available);
        return available;
    }

    public static boolean hasNativeAnimation(String id) {
        if (id == null || id.isBlank()) return false;
        return NATIVE_CACHE.computeIfAbsent(NpcModelOwnership.normalize(id),NpcAnimationInspector::hasNativeWalk);
    }
    private static boolean hasNativeWalk(String key) {
        // Compiled Generic assets ship on both sides; never depend on client renderer classes.
        String namespace=key.contains(":") ? key.split(":",2)[0] : "stardewcraft";
        String name=key.contains(":") ? key.split(":",2)[1] : key;
        String path = "assets/"+namespace+"/npc_native/"+name+".json";
        try (InputStream stream = NpcAnimationInspector.class.getClassLoader().getResourceAsStream(path)) {
            if (stream == null) return false;
            JsonObject root = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            if (!root.has("clips") || !root.get("clips").isJsonObject()) return false;
            var clip = root.getAsJsonObject("clips").get("animation." + key + ".walk");
            if (clip == null || !clip.isJsonObject()) return false;
            var tracks = clip.getAsJsonObject().get("tracks");
            return tracks != null && tracks.isJsonArray() && !tracks.getAsJsonArray().isEmpty();
        } catch (Exception ignored) {
            return false;
        }
    }

    private static boolean hasLegacyWalk(String key) {
        String path = "assets/stardewcraft/animations/entity/npc/" + key + ".animation.json";
        try (InputStream stream = NpcAnimationInspector.class.getClassLoader().getResourceAsStream(path)) {
            if (stream == null) {
                return false;
            }
            JsonObject root = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            if (!root.has("animations") || !root.get("animations").isJsonObject()) {
                return false;
            }

            JsonObject animations = root.getAsJsonObject("animations");
            for (String animKey : animations.keySet()) {
                String lower = animKey.toLowerCase(Locale.ROOT);
                if (lower.equals("walk") || lower.contains("walk")) {
                    return true;
                }
            }
            return false;
        } catch (Exception ignored) {
            return false;
        }
    }
}
