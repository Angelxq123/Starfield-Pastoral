package com.stardew.craft.building.runtime;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BuildingBlueprintPreviewTest {
    @Test void heldBlueprintsMustNotNeedTheServerPrefabTableOnARemoteClient() {
        var blueprints = BuiltInRegistries.ITEM.stream().filter(BuildingBlueprintItem.class::isInstance)
                .map(BuildingBlueprintItem.class::cast).toList();
        assertTrue(blueprints.size() >= 4);
        assertAll(blueprints.stream().map(item -> () -> {
            assertFalse(PrefabDefinitions.available(item.family()), "Headless client must have no server definitions");
            assertNull(BuildingBlueprintItem.previewTargetAnchor(new ItemStack(item), new BlockPos(10, 64, 10), Direction.SOUTH),
                    item.family().toString());
        }));
    }
    @Test void syncedGeometryWorksForAllFamiliesWithoutAnyServerDefinitions() throws Exception {
        for (var item : BuiltInRegistries.ITEM.stream().filter(BuildingBlueprintItem.class::isInstance)
                .map(BuildingBlueprintItem.class::cast).toList()) {
            assertFalse(PrefabDefinitions.available(item.family()));
            try (var stream = getClass().getResourceAsStream("/data/" + item.family().getNamespace()
                    + "/farm_building_prefabs/" + item.family().getPath() + ".json")) {
                assertNotNull(stream);
                var json = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                int depth = json.getAsJsonObject("reservation_bounds_from_anchor").getAsJsonArray("max_exclusive").get(2).getAsInt();
                var stack = new ItemStack(item);
                var tag = new net.minecraft.nbt.CompoundTag(); tag.putInt("BlueprintPreviewDepth", depth);
                stack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(tag));
                var ground = new BlockPos(10, 64, -10);
                for (var facing : Direction.Plane.HORIZONTAL) {
                    var expected = ground.subtract(PrefabDefinitions.rotateCell(new BlockPos(0, 0, depth - 1), PrefabDefinitions.rotation(facing)));
                    assertEquals(expected, BuildingBlueprintItem.previewTargetAnchor(stack, ground, facing), item.family().toString());
                }
                // Self-built move documents already carry their bounds, even before the first inventory sync.
                tag.remove("BlueprintPreviewDepth"); tag.putBoolean("MoveSelf", true);
                tag.putLong("MoveMin", new BlockPos(-2, -3, -2).asLong());
                stack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(tag));
                assertEquals(ground.above(4), BuildingBlueprintItem.previewTargetAnchor(stack, ground, Direction.SOUTH));
            }
        }
    }

    @Test void actualClientTickUsesTheRemoteSafeAnchorPath() throws Exception {
        var node = new org.objectweb.asm.tree.ClassNode();
        try (var stream = getClass().getResourceAsStream("/com/stardew/craft/client/building/BuildingPlacementPreview.class")) {
            assertNotNull(stream); new org.objectweb.asm.ClassReader(stream).accept(node, 0);
        }
        int safeCalls = 0;
        for (var method : node.methods) for (var insn : method.instructions) {
            if (insn instanceof org.objectweb.asm.tree.MethodInsnNode call &&
                    call.owner.equals("com/stardew/craft/building/runtime/BuildingBlueprintItem")) {
                assertNotEquals("targetAnchor", call.name, "Client must never read the server prefab table");
                if (call.name.equals("previewTargetAnchor")) safeCalls++;
            }
        }
        assertEquals(1, safeCalls);
    }
}
