package com.stardew.craft.monster;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import com.stardew.craft.client.monsternative.NativeBatMotion;
import com.stardew.craft.client.npcnative.NativeNpcModel;
import com.stardew.craft.client.npcnative.NativeNpcPose;
import com.stardew.craft.entity.ModEntities;

import com.stardew.craft.entity.monster.MineBatEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class BatIntegrationTest {
    private InputStreamReader resource(String path) {
        var stream = getClass().getClassLoader().getResourceAsStream(path);
        assertNotNull(stream, path);
        return new InputStreamReader(stream, StandardCharsets.UTF_8);
    }
    private NativeNpcModel model() throws Exception {
        try (var reader = resource("assets/stardewcraft/monster_native/bat.json")) {
            return new Gson().fromJson(reader, NativeNpcModel.class);
        }
    }
    @Test void nativeIdentityAndSourceTableHaveNoPhantomOrFloorMultiplier() throws Exception {
        assertEquals("entity.stardewcraft.bat", ModEntities.BAT.get().getDescriptionId());
        try (var reader = resource("data/stardewcraft/monsters/bat.json")) {
            var definition = MonsterDefinition.parse(MonsterDefinitions.BAT, JsonParser.parseReader(reader).getAsJsonObject());
            assertEquals("Bat", definition.sourceName()); assertEquals("bat", definition.family());
            assertEquals(24, definition.health()); assertEquals(6, definition.damage());
            assertEquals(1, definition.resilience()); assertEquals(0, definition.missChance());
            assertEquals(3, definition.experience());
            assertEquals(List.of(.9, .4, .001, .02, .005, .001), definition.drops().stream().map(MonsterDefinition.Drop::chance).toList());
            assertEquals(List.of("bat_wing", "bat_wing", "rare_disc", "bomb_item", "dwarf_scroll_i", "dwarf_scroll_iv"),
                    definition.drops().stream().map(d -> ResourceLocation.parse(d.item()).getPath()).toList());
            for (var drop : definition.drops()) assertTrue(BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse(drop.item())), drop.item());
            for (int floor : new int[]{1, 29, 39}) {
                var base = MonsterStatResolver.base(definition,
                        new MonsterSpawnContext(MonsterSpawnContext.Source.ORDINARY_MINE, floor, false, null), RandomSource.create(1));
                assertEquals(24, base.initialHealth()); assertEquals(6, base.combat().getDamage());
            }
            for (int seed = 0; seed < 100; seed++) {
                var context = new MonsterSpawnContext(MonsterSpawnContext.Source.COMMAND, 30, true, null);
                var stats = MonsterStatResolver.base(definition, context, RandomSource.create(seed));
                assertTrue(stats.initialHealth() >= 24 && stats.initialHealth() < 48);
                assertTrue(stats.combat().getDamage() >= 6 && stats.combat().getDamage() <= 8);
                var state = new MonsterState(definition, context, RandomSource.create(seed));
                assertEquals(state.save(), MonsterState.load(state.save()).save());
            }
        }
    }
    @Test void allBatVariantsUseTheirOwnSourceStatsDropsAndNativeModels() throws Exception {
        String[] ids = {"frost_bat", "lava_bat", "iridium_bat"};
        int[] hp = {36, 80, 300}, damage = {7, 15, 30}, xp = {7, 15, 22};
        for (int i = 0; i < ids.length; i++) {
            String id = ids[i];
            try (var reader = resource("data/stardewcraft/monsters/" + id + ".json")) {
                var definition = MonsterDefinition.parse(ResourceLocation.parse("stardewcraft:" + id), JsonParser.parseReader(reader).getAsJsonObject());
                assertEquals(hp[i], definition.health()); assertEquals(damage[i], definition.damage()); assertEquals(xp[i], definition.experience());
                for (var drop : definition.drops()) assertTrue(BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse(drop.item())), drop.item());
                if (id.equals("iridium_bat")) {
                    assertEquals(List.of(.9,.5,.25,.1,.05,.5,.05,.05,.05,.008), definition.drops().stream().map(MonsterDefinition.Drop::chance).toList());
                    assertTrue(definition.drops().stream().noneMatch(d -> d.item().endsWith("bat_wing")));
                } else assertEquals(i == 0 ? .55 : .7, definition.drops().get(1).chance());
            }
            try (var reader = resource("assets/stardewcraft/monster_native/" + id + ".json")) {
                var model = new Gson().fromJson(reader, NativeNpcModel.class);
                assertEquals("stardewcraft:textures/entity/monster_native/" + id + ".png", model.texture());
                var pose = new NativeNpcPose(model);
                for (int phase = 0; phase < 3; phase++) for (int frame = 0; frame <= 112; frame++) {
                    double time = frame / 100.0;
                    NativeBatMotion.sample(pose, phase, time);
                    float lift = NativeBatMotion.lift(model, pose.matrices(), phase, time);
                    for (var q : model.quads()) {
                        if (q.sourcePart().contains("membrane")) continue;
                        for (var v : q.vertices()) {
                            var vertex = pose.matrices()[q.bone()].transformPosition(new Vector3f(v[0],v[1],v[2]));
                            assertTrue(vertex.y / 16 + lift >= .019 && vertex.y / 16 + lift <= MineBatEntity.HEIGHT - .019,
                                    id + " solid body/hull outside physics box");
                        }
                    }
                }
            }
        }
    }
    @Test void iridiumSourceAddsAccelerationAndUsesThreeTileNearRange() {
        var common = new BatSourceFlight(); var iridium = new BatSourceFlight();
        common.rotation = iridium.rotation = -Math.PI / 2;
        iridium.extraVelocity = 1;
        common.steer(-640, 0, false, 0, 0); iridium.steer(-640, 0, false, 0, 0);
        assertTrue(Math.abs(iridium.x) > Math.abs(common.x));
        iridium.x = 4; iridium.rotation = -Math.PI / 2;
        iridium.steer(-128, 0, false, 0, 0);
        assertEquals(4 * 1.05 - 5.0 / 6, iridium.x, 1e-8);
    }
    @Test void awarenessUsesSquareTilesIncludingDiagonalAndNegativeCoordinates() {
        assertTrue(BatSourceFlight.withinNoticeRange(0, 0, 6, 6));
        assertTrue(BatSourceFlight.withinNoticeRange(-10, -10, -16, -4));
        assertFalse(BatSourceFlight.withinNoticeRange(0, 0, 7, 0));
        assertFalse(BatSourceFlight.withinNoticeRange(0, 0, 0, -7));
    }
    @Test void steeringMapsBothSourceAxesAndHitLocksTurnForHalfSecond() {
        for (double[] target : new double[][]{{640,0}, {-640,0}, {0,640}, {0,-640}}) {
            var flight = new BatSourceFlight();
            flight.rotation = Math.atan2(-target[1], -target[0]) - Math.PI / 2;
            flight.hitMillis = 500;
            double before = flight.rotation;
            flight.steer(target[0], target[1], false, 0, 0);
            assertEquals(before, flight.rotation);
            assertEquals(Math.signum(target[0]) / 6, flight.x, 1e-9);
            assertEquals(Math.signum(target[1]) / 6, flight.z, 1e-9);
            flight.hitMillis = 0;
            flight.steer(-target[0], -target[1], false, 0, 0);
            assertEquals(Math.PI / 64, Math.abs(flight.rotation - before), 1e-9);
        }
    }
    @Test void inertiaDecayAndSixtyHzClockMatchSource() {
        assertEquals(60, BatSourceFlight.STEPS_PER_TICK * 20);
        var flight = new BatSourceFlight(); flight.x = 5; flight.z = -3; flight.slipperiness = 20;
        flight.decay(false);
        assertEquals(4.75, flight.x); assertEquals(-2.85, flight.z);
        flight.x = 5; flight.decay(true); assertEquals(4.9375, flight.x);
        for (int step = 0; step < 240; step++) flight.decay(false);
        assertEquals(0, flight.x); assertEquals(0, flight.z);
    }
    @Test void approvedClipsJoinExactlyAndLateObserversSampleCurrentFlight() throws Exception {
        var model = model(); var left = new NativeNpcPose(model); var right = new NativeNpcPose(model);
        assertEquals(.32, model.clips().get("animation.bat.fly").length());
        for (double[] pair : new double[][]{{0, 0, 1, 0}, {1, .56, 2, 0}, {1, .56 + .32 * 70 + .1, 2, .1}}) {
            NativeBatMotion.sample(left, (int) pair[0], pair[1]);
            NativeBatMotion.sample(right, (int) pair[2], pair[3]);
            for (int i = 0; i < model.bones().size(); i++)
                assertArrayEquals(left.matrices()[i].get(new float[16]), right.matrices()[i].get(new float[16]), .0001F);
        }
    }
    @Test void headHullStaysInsideStableAabbAtEveryPoseAndYaw() throws Exception {
        var model = model(); var pose = new NativeNpcPose(model); var vertex = new Vector3f();
        assertEquals(52, model.quads().size());
        for (int action : new int[]{0, 1, 2}) for (int frame = 0; frame <= 560; frame++) {
            double time = action == 1 ? frame / 1000.0 : frame / 560.0 * .32;
            NativeBatMotion.sample(pose, action, time); var matrices = pose.matrices();
            float lift = NativeBatMotion.lift(model, matrices, action, time);
            for (var quad : model.quads()) {
                assertFalse(quad.translucent(), "Cutout wings and culled hull, no translucent sorting");
                if (!quad.sourcePart().equals("head_outline")) continue;
                for (var v : quad.vertices()) {
                    matrices[quad.bone()].transformPosition(vertex.set(v[0], v[1], v[2]));
                    double radius = Math.hypot(vertex.x, vertex.z) / 16;
                    assertTrue(radius < MineBatEntity.WIDTH / 2, action + " at " + time + " radius " + radius);
                    double y = vertex.y / 16 + lift;
                    assertTrue(y >= .0199 && y <= MineBatEntity.HEIGHT - .0199,
                            action + " at " + time + " y " + y);
                }
            }
        }
    }
    @Test void textureSoundAndAllLocalizedEntityNamesExist() throws Exception {
        try (var texture = getClass().getClassLoader().getResourceAsStream("assets/stardewcraft/textures/entity/monster_native/bat.png")) {
            assertNotNull(texture); assertTrue(texture.readAllBytes().length > 0);
        }
        for (String language : List.of("en_us", "zh_cn", "tr_tr", "ja_jp", "ko_kr", "fr_fr", "de_de", "es_es", "it_it", "pt_br", "ru_ru", "hu_hu")) {
            try (var reader = resource("assets/stardewcraft/lang/" + language + ".json")) {
                var json = JsonParser.parseReader(reader).getAsJsonObject();
                assertEquals(json.get("entity.stardewcraft.mine_monster.bat"), json.get("entity.stardewcraft.bat"));
            }
        }
        for (String sound : List.of("hit_enemy", "bat_screech", "entity/crow/flap"))
            try (var resource = getClass().getClassLoader().getResourceAsStream("assets/stardewcraft/sounds/" + sound + ".ogg")) { assertNotNull(resource); }
    }
}
