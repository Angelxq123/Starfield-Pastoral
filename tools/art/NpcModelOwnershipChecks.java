import com.google.gson.JsonParser;
import com.stardew.craft.npc.data.NpcModelOwnership;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Check actual packaged replacement ownership and absence of retired resources. */
public final class NpcModelOwnershipChecks {
    public static void main(String[] args) throws Exception {
        var ids = NpcModelOwnership.nativeIds();
        var loader = NpcModelOwnershipChecks.class.getClassLoader();
        NpcModelOwnership.requireComplete(ids);
        for (String id : ids) {
            for (String alias : List.of(id, "STARDEWCRAFT:" + id.toUpperCase(java.util.Locale.ROOT))) {
                if (!NpcModelOwnership.requiresNative(alias)) throw new AssertionError("Ownership alias: " + alias);
                mustFail(() -> NpcModelOwnership.requireLegacy(alias), "Legacy renderer accepted " + alias);
            }
            var missing = new HashSet<>(ids);
            missing.remove(id);
            mustFail(() -> NpcModelOwnership.requireComplete(missing), "Missing model accepted: " + id);
            for (String path : List.of("assets/stardewcraft/geo/entity/npc/" + id + ".geo.json",
                    "assets/stardewcraft/animations/entity/npc/" + id + ".animation.json",
                    "assets/stardewcraft/textures/entity/npc/" + id + ".png")) {
                if (loader.getResource(path) != null) throw new AssertionError("Retired resource on classpath: " + path);
                for (String root : args) if (Files.exists(Path.of(root, path)))
                    throw new AssertionError("Retired resource remains: " + root + "/" + path);
            }
            for (String path : List.of("assets/stardewcraft/npc_native/" + id + ".json",
                    "assets/stardewcraft/textures/entity/npc_native/" + id + ".png"))
                if (loader.getResource(path) == null) throw new AssertionError("Missing current resource: " + path);
        }
        // Retiring replaced characters must preserve independent actors without a replacement.
        for (String id : Set.of("gunther", "morris", "traveling_cart")) {
            if (NpcModelOwnership.requiresNative(id)) continue;
            if (!NpcModelOwnership.requireLegacy(id).equals(id)) throw new AssertionError("Unmigrated ID changed");
            if (loader.getResource("assets/stardewcraft/geo/entity/npc/" + id + ".geo.json") == null)
                throw new AssertionError("Unmigrated model removed: " + id);
        }
        for (String root : args) {
            var variants = JsonParser.parseString(Files.readString(Path.of(root,
                    "assets/xaerominimap/entity/icon/definition/stardewcraft/stardew_npc.json")))
                    .getAsJsonObject().getAsJsonObject("variants");
            for (String id : ids) if (variants.has("stardewcraft:textures/entity/npc/" + id + ".png"))
                throw new AssertionError("Minimap still selects retired texture: " + id);
        }
        System.out.println("PASS: " + ids.size() + " native IDs own their models; missing replacements and direct legacy calls rejected;"
                + " retired geometry/textures/animations absent from source and packaged resources; unmigrated actors preserved.");
    }

    private static void mustFail(Runnable check, String message) {
        try { check.run(); } catch (IllegalStateException expected) { return; }
        throw new AssertionError(message);
    }
}
