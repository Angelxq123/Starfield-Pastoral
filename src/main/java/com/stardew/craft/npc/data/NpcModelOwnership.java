package com.stardew.craft.npc.data;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

/** Shipped replacements own their NPC IDs even if a resource reload fails. */
public final class NpcModelOwnership {
    private static final Set<String> NATIVE_IDS = load();

    private NpcModelOwnership() {}

    public static String normalize(String id) {
        String value = id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
        return value.startsWith("stardewcraft:") ? value.substring(13) : value;
    }

    public static Set<String> nativeIds() { return NATIVE_IDS; }
    public static boolean requiresNative(String id) { return NATIVE_IDS.contains(normalize(id)); }

    public static String requireLegacy(String id) {
        String normalized = normalize(id);
        if (normalized.isEmpty() || requiresNative(normalized))
            throw new IllegalStateException("Legacy NPC model is forbidden for replaced character: " + id);
        return normalized;
    }

    public static void requireComplete(Set<String> loaded) {
        var missing = new TreeSet<>(NATIVE_IDS);
        missing.removeAll(loaded);
        if (!missing.isEmpty()) throw new IllegalStateException("Missing required native NPC models: " + missing);
    }

    private static Set<String> load() {
        // Use the packaged ownership manifest, not a resource pack override that could undo a migration.
        try (var stream = NpcModelOwnership.class.getClassLoader()
                .getResourceAsStream("assets/stardewcraft/npc_native_models.json")) {
            if (stream == null) throw new IllegalStateException("Missing native NPC ownership manifest");
            var ids = new TreeSet<String>();
            for (var value : JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonArray())
                if (!ids.add(value.getAsString())) throw new IllegalStateException("Duplicate native NPC owner");
            if (ids.isEmpty()) throw new IllegalStateException("Empty native NPC ownership manifest");
            return Set.copyOf(ids);
        } catch (java.io.IOException error) {
            throw new IllegalStateException("Cannot read native NPC ownership manifest", error);
        }
    }
}
