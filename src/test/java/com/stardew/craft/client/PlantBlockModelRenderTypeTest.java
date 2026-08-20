package com.stardew.craft.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PlantBlockModelRenderTypeTest {
    private static final String VANILLA_CROSS_PARENT = "minecraft:block/cross";
    private static final String MOD_CROSS_PARENT = "stardewcraft:block/template/cutout_cross";
    private static final String CUTOUT_RENDER_TYPE = "minecraft:cutout";

    @Test
    void plantModelsUsePrivateCutoutCrossParent() throws IOException {
        Path projectDir = Path.of(System.getProperty("stardewcraft.projectDir", "."));
        Path modelRoot = projectDir.resolve("src/main/resources/assets/stardewcraft/models/block");
        List<String> invalidModels = new ArrayList<>();

        try (var paths = Files.walk(modelRoot)) {
            paths.filter(path -> path.toString().endsWith(".json"))
                    .forEach(path -> validateCrossModel(modelRoot, path, invalidModels));
        }

        assertTrue(invalidModels.isEmpty(),
                "Plant block models must use the private cutout cross parent:\n"
                        + String.join("\n", invalidModels));
    }

    @Test
    void privateCutoutCrossParentContainsGeometry() throws IOException {
        Path projectDir = Path.of(System.getProperty("stardewcraft.projectDir", "."));
        Path template = projectDir.resolve(
                "src/main/resources/assets/stardewcraft/models/block/template/cutout_cross.json");

        assertTrue(Files.isRegularFile(template), "Missing private cutout cross parent: " + template);
        JsonObject model = JsonParser.parseString(Files.readString(template)).getAsJsonObject();
        assertTrue(CUTOUT_RENDER_TYPE.equals(getString(model, "render_type")),
                "Private cross parent must use minecraft:cutout");
        assertTrue(model.has("elements") && model.getAsJsonArray("elements").size() >= 2,
                "Private cross parent must contain block geometry");
    }

    private static void validateCrossModel(Path modelRoot, Path path, List<String> invalidModels) {
        try {
            JsonObject model = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            String parent = getString(model, "parent");
            if (VANILLA_CROSS_PARENT.equals(parent)) {
                invalidModels.add(modelRoot.relativize(path).toString().replace('\\', '/')
                        + " (inherits overrideable minecraft:block/cross)");
                return;
            }
            if (MOD_CROSS_PARENT.equals(parent)
                    && !CUTOUT_RENDER_TYPE.equals(getString(model, "render_type"))) {
                invalidModels.add(modelRoot.relativize(path).toString().replace('\\', '/'));
            }
        } catch (RuntimeException | IOException exception) {
            invalidModels.add(modelRoot.relativize(path).toString().replace('\\', '/')
                    + " (unreadable JSON: " + exception.getMessage() + ")");
        }
    }

    private static String getString(JsonObject object, String key) {
        return object.has(key) && object.get(key).isJsonPrimitive()
                ? object.get(key).getAsString()
                : "";
    }
}
