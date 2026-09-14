import com.google.gson.Gson;
import com.google.gson.JsonParser;
import com.stardew.craft.client.npcnative.NativeActorAnimation;
import com.stardew.craft.client.npcnative.NativeNpcModel;
import com.stardew.craft.client.npcnative.NativeNpcPose;
import com.stardew.craft.npc.animation.SamActivity;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;

/** Exercise scripted playback against shipped native resources, without a game client. */
public final class NativeActorAnimationChecks {
    public static void main(String[] args) throws Exception {
        var gson = new Gson();
        var models = new HashMap<String, NativeNpcModel>();
        try (var files = Files.list(Path.of(args[0]))) {
            for (var file : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                models.put(file.getFileName().toString().replace(".json", ""),
                        gson.fromJson(Files.readString(file), NativeNpcModel.class));
            }
        }
        int commands = 0;
        // Check actual cutscene aliases, including Willy fishing -> idle and Pam idle.
        try (var files = Files.list(Path.of(args[1]))) {
            for (var file : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                var root = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
                var actors = new HashMap<String, String>();
                for (var value : root.getAsJsonArray("commands")) {
                    var command = value.getAsJsonObject();
                    String type = command.get("cmd").getAsString();
                    if (type.equals("spawn_actor"))
                        actors.put(command.get("actor").getAsString(), command.get("npc_id").getAsString());
                    if (!type.equals("animate")) continue;
                    String id = actors.get(command.get("actor").getAsString());
                    var model = models.get(id);
                    if (model == null) continue; // Player actors and characters not yet migrated.
                    String request = command.get("anim").getAsString();
                    String clip = NativeActorAnimation.resolve(model, id, request);
                    if (clip == null) {
                        if (!(id.equals("willy") && request.equals("fishing")))
                            throw new AssertionError("Unresolved cutscene clip: " + id + "/" + request);
                        if (Files.exists(Path.of(args[2], "willy.animation.json")))
                            throw new AssertionError("Retired Willy animation is still packaged");
                        // Missing actions retain this same native model and its normal idle pose.
                        clip = NativeActorAnimation.resolve(model, id, "idle");
                    }
                    var pose = new NativeNpcPose(model);
                    NativeActorAnimation.apply(pose, model, clip, .37, command.get("loop").getAsBoolean());
                    finite(pose);
                    commands++;
                }
            }
        }
        int clips = 0;
        for (var entry : models.entrySet()) {
            var model = entry.getValue();
            for (String name : model.clips().keySet()) {
                if (!name.equals(NativeActorAnimation.resolve(model, entry.getKey(), name)))
                    throw new AssertionError("Fully qualified clip lost: " + name);
                String prefix = "animation." + entry.getKey() + ".";
                if (name.startsWith(prefix) && !name.equals(NativeActorAnimation.resolve(model, entry.getKey(), name.substring(prefix.length()))))
                    throw new AssertionError("Short alias lost: " + name);
                clips++;
            }
        }
        for (var action : SamActivity.values()) {
            var model = models.get(action.asset());
            for (String clip : List.of(action.playClip(), action.holdClip())) {
                var pose = new NativeNpcPose(model);
                NativeActorAnimation.apply(pose, model, clip, 20.37, false);
                finite(pose);
            }
        }
        timing(models.get("willy"));
        if (commands == 0 || clips == 0) throw new AssertionError("No shipped actor animations checked");
        System.out.println("PASS: " + commands + " shipped NPC animate commands; " + clips
                + " native clip names/aliases; Sam activities, script loop overrides, finite joint surfaces.");
        System.out.println("Known content gap: Willy fishing is not authored; current native idle is retained.");
    }

    private static void timing(NativeNpcModel source) {
        var keys = List.of(new NativeNpcModel.Key(0, new float[]{0,0,0}, new float[]{0,0,0}, false),
                new NativeNpcModel.Key(1, new float[]{10,0,0}, new float[]{10,0,0}, false));
        for (boolean authoredLoop : new boolean[]{false, true}) {
            var clip = new NativeNpcModel.Clip(1, authoredLoop,
                    List.of(new NativeNpcModel.Track(0, "position", keys)));
            var model = new NativeNpcModel(source.version(), source.texture(), source.bones(), source.quads(),
                    java.util.Map.of("script", clip), source.profile());
            for (boolean scriptLoop : new boolean[]{false, true}) {
                for (double elapsed : new double[]{-.5, .25, 1, 1.25, 9.25}) {
                    var pose = new NativeNpcPose(model);
                    NativeActorAnimation.apply(pose, model, "script", elapsed, scriptLoop);
                    double expected = (scriptLoop ? Math.max(0,elapsed) % 1 : Math.clamp(elapsed,0,1)) * 10;
                    if (Math.abs(pose.matrices()[0].m30() - expected) > 1e-5)
                        throw new AssertionError("Script once/loop timing at " + elapsed);
                }
            }
        }
    }

    private static void finite(NativeNpcPose pose) {
        var matrices = pose.matrices();
        for (var matrix : matrices) if (!matrix.isFinite()) throw new AssertionError("Invalid actor matrix");
        var surface = pose.surfaceVertices(matrices);
        if (surface != null) for (var quad : surface) if (quad != null)
            for (var vertex : quad) for (float coordinate : vertex)
                if (!Float.isFinite(coordinate)) throw new AssertionError("Invalid actor joint surface");
    }
}
