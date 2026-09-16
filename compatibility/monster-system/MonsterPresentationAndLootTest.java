package com.stardew.craft.monster;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import com.stardew.craft.client.monsternative.NativeMonsterPresentation;
import com.stardew.craft.client.npcnative.NativeNpcModel;
import com.stardew.craft.client.npcnative.NativeNpcPose;
import com.stardew.craft.festival.desert.DesertFestivalMineService;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class MonsterPresentationAndLootTest {
    private record AntennaReference(String clip, double time, float[] position) {}
    @Test void antennaPlaybackMatchesSavedBlockbenchPoses() throws Exception {
        try (var reader = resource("assets/stardewcraft/monster_native/green_slime.json")) {
            var model = new Gson().fromJson(reader, NativeNpcModel.class);
            var pose = new NativeNpcPose(model);
            AntennaReference[] references;
            try (var referenceReader = resource("antenna-blockbench.json")) {
                references = new Gson().fromJson(referenceReader, AntennaReference[].class);
            }
            int bone = java.util.stream.IntStream.range(0, model.bones().size())
                    .filter(i -> model.bones().get(i).name().equals("antenna")).findFirst().orElseThrow();
            for (var reference : references) {
                pose.reset(); pose.apply(reference.clip(), reference.time());
                var position = pose.matrices()[bone].transformPosition(new Vector3f(1.5F, 14, 1.5F));
                assertArrayEquals(reference.position(), new float[]{position.x, position.y, position.z}, .0002F,
                        reference.clip() + " at " + reference.time());
            }
        }
    }
    @Test void sourceAntennaRulesExcludeBabiesAndNeverDrawBothTips() {
        for (boolean adult : new boolean[]{false, true}) for (boolean male : new boolean[]{false, true})
            for (boolean marked : new boolean[]{false, true}) {
                int kind = com.stardew.craft.entity.monster.GreenSlimeRules.antenna(male, marked, adult);
                assertEquals(adult && (male || marked), NativeMonsterPresentation.slimePartVisible("antenna_stem", kind));
                assertEquals(adult && male && !marked, NativeMonsterPresentation.slimePartVisible("male_tip", kind));
                assertEquals(adult && marked, NativeMonsterPresentation.slimePartVisible("special_tip", kind));
                assertTrue(NativeMonsterPresentation.slimePartVisible("gel_outline", kind));
            }
    }
    private InputStreamReader resource(String path) {
        var stream = getClass().getClassLoader().getResourceAsStream(path);
        assertNotNull(stream, path);
        return new InputStreamReader(stream, StandardCharsets.UTF_8);
    }
    @Test void everyAnimatedShellVertexClearsGroundIncludingBabiesAndDeathRoll() throws Exception {
        try (var reader = resource("assets/stardewcraft/monster_native/green_slime.json")) {
            var model = new Gson().fromJson(reader, NativeNpcModel.class);
            var pose = new NativeNpcPose(model);
            var v = new Vector3f();
            for (var entry : model.clips().entrySet()) for (int frame = 0; frame <= 120; frame++) {
                pose.reset(); pose.apply(entry.getKey(), entry.getValue().length() * frame / 120);
                var bones = pose.matrices();
                for (float growth : new float[]{.2F, .5F, 1, 1.5F}) for (int death = 0; death <= 20; death++) {
                    var body = NativeMonsterPresentation.deathTransform(death, 4);
                    float scale = growth / 16, lift = NativeMonsterPresentation.groundLift(model, bones, body, scale);
                    float min = Float.POSITIVE_INFINITY;
                    for (var quad : model.quads()) for (var vertex : quad.vertices()) {
                        bones[quad.bone()].transformPosition(v.set(vertex[0], vertex[1], vertex[2]));
                        body.transformPosition(v); min = Math.min(min, v.y * scale + lift);
                    }
                    assertTrue(min >= NativeMonsterPresentation.GROUND_CLEARANCE - .00001F,
                            entry.getKey() + " frame=" + frame + " death=" + death);
                    assertTrue(min < NativeMonsterPresentation.GROUND_CLEARANCE + .00001F);
                }
            }
        }
    }
    @Test void idleActuallyMovesButRetainsLoopAndFaceShape() throws Exception {
        try (var reader = resource("assets/stardewcraft/monster_native/green_slime.json")) {
            var model = new Gson().fromJson(reader, NativeNpcModel.class);
            var pose = new NativeNpcPose(model);
            String idle = "animation.slime.idle";
            pose.apply(idle, 0); var first = new org.joml.Matrix4f(pose.matrices()[1]);
            pose.reset(); pose.apply(idle, 1.1);
            assertFalse(first.equals(pose.matrices()[1], .001F));
            assertEquals(1, pose.matrices()[1].determinant(), .001F);
            assertEquals(1, pose.matrices()[2].m00(), .001F);
            assertEquals(1, pose.matrices()[2].m11(), .001F);
            pose.reset(); pose.apply(idle, model.clips().get(idle).length());
            assertTrue(first.equals(pose.matrices()[1], .00001F));
        }
    }
    @Test void collisionBoxFitsGroundedOuterHullWithoutSweptAnimationPadding() throws Exception {
        try(var reader=resource("assets/stardewcraft/monster_native/green_slime.json")) {
            var model=new Gson().fromJson(reader,NativeNpcModel.class);
            var pose=new NativeNpcPose(model);
            pose.apply("animation.slime.idle",0);
            var bones=pose.matrices();
            for(int antenna=0;antenna<3;antenna++)for(float growth:new float[]{.2F,1F,1.5F})for(int yaw=0;yaw<360;yaw+=15) {
                var box=com.stardew.craft.entity.monster.GreenSlimeRules.collisionBox(0,0,0,yaw,growth,antenna);
                double lift=NativeMonsterPresentation.groundLift(model,bones,new org.joml.Matrix4f(),growth/16,antenna);
                var rotation=new org.joml.Matrix4f().rotateY((float)Math.toRadians(180-yaw));
                double minX=Double.POSITIVE_INFINITY,maxX=Double.NEGATIVE_INFINITY;
                for(var q:model.quads()) {
                    if(!NativeMonsterPresentation.slimePartVisible(q.sourcePart(),antenna))continue;
                    for(var v:q.vertices()) {
                        var point=bones[q.bone()].transformPosition(new Vector3f(v[0],v[1],v[2])).mul(growth/16);
                        rotation.transformPosition(point);point.y+=lift;
                        assertTrue(box.contains(point.x,point.y,point.z), "Rest hull must fit; antenna="+antenna+" part="+q.sourcePart()+" p="+point+" box="+box);
                        if(q.sourcePart().equals("gel_outline")){minX=Math.min(minX,point.x);maxX=Math.max(maxX,point.x);}
                    }
                }
                assertTrue(box.getXsize()-(maxX-minX)<.081*growth,"Padding must remain small");
                assertTrue(box.getXsize()<1.21*growth,"Antenna must not inflate horizontal contact");
            }
        }
    }
    @Test void ordinaryMovementStaysGroundedAndLeapsScaleWithBody() {
        for (double t = 0; t < 4; t += .01) {
            assertEquals(0, NativeMonsterPresentation.slimeLift(false, false, t, 1));
            assertEquals(0, NativeMonsterPresentation.slimeLift(true, true, t, 1));
        }
        assertEquals(.48, NativeMonsterPresentation.slimeLift(true, false, .325, 1), .00001);
        assertEquals(.288, NativeMonsterPresentation.slimeLift(true, false, .325, .6F), .00001);
    }
    @Test void movementStopsAndInterruptedPosesBlendWithoutRestartPops() throws Exception {
        try (var reader = resource("assets/stardewcraft/monster_native/green_slime.json")) {
            var model = new Gson().fromJson(reader, NativeNpcModel.class);
            var motion = new com.stardew.craft.client.monsternative.NativeSlimeMotion(model);
            motion.sample(1, 1, 0, 0, 0, 0, 1, false);
            for (int frame = 1; frame <= 50; frame++) motion.sample(1, 1, frame / 100.0,
                    frame / 100.0, frame / 100.0, 0, 1, false);
            double clock = .5;
            for (int action : new int[]{2, 3, 4, 0, 1, 2}) {
                var before = new org.joml.Matrix4f(motion.pose().matrices()[1]);
                double lift = motion.lift();
                clock += .000001;
                motion.sample(action, action + 10, 0, clock, .5, 0, 1, false);
                assertTrue(before.equals(motion.pose().matrices()[1], .00001F), "Transition changed its initial pose");
                assertEquals(lift, motion.lift(), .00001);
                for (int frame = 1; frame <= 8; frame++) {
                    clock += .01;
                    motion.sample(action, action + 10, frame / 100.0, clock, .5, 0, 1, false);
                }
            }
            for (int frame = 1; frame <= 100; frame++) {
                clock += .01;
                motion.sample(1, 100, frame / 100.0, clock, .5, 0, 1, false);
            }
            assertEquals(0, motion.lift(), .00001, "Stale WALK after collision must settle");
            var idle = new NativeNpcPose(model);
            idle.apply("animation.slime.idle", clock);
            assertTrue(idle.matrices()[1].equals(motion.pose().matrices()[1], .00001F));
            var before = new org.joml.Matrix4f(motion.pose().matrices()[1]);
            motion.sample(1, 100, 1, clock, .5, 0, 1, false);
            assertTrue(before.equals(motion.pose().matrices()[1], .00001F));
        }
    }
    @Test void visualRimIsBrighterAndMoreChromaticWhileLootUsesOriginalColor() {
        int body = NativeMonsterPresentation.slimeTint(0x55E01F, false);
        int outline = NativeMonsterPresentation.slimeTint(0x55E01F, true);
        assertEquals(0x74E647, body); // Preserve the approved body tint.
        int bodyMax = 0, bodyMin = 255, rimMax = 0, rimMin = 255;
        for (int shift : new int[]{0, 8, 16}) {
            bodyMax = Math.max(bodyMax, body >> shift & 255);
            bodyMin = Math.min(bodyMin, body >> shift & 255);
            rimMax = Math.max(rimMax, outline >> shift & 255);
            rimMin = Math.min(rimMin, outline >> shift & 255);
        }
        assertTrue(rimMax > bodyMax);
        assertTrue((rimMax - rimMin) / (float) rimMax > (bodyMax - bodyMin) / (float) bodyMax);
        // Full-brightness green still needs hue separation; V has no remaining headroom.
        int saturatedBody = NativeMonsterPresentation.slimeTint(0x00FF00, false);
        int saturatedRim = NativeMonsterPresentation.slimeTint(0x00FF00, true);
        assertTrue((saturatedRim >> 16 & 255) - (saturatedBody >> 16 & 255) > 70);
        assertEquals(List.of(drop("388", 3), drop("709", 1)), color(0x46280A, 0, true, 0));
    }
    @Test void deathRollReachesNinetyDegreesContinuously() {
        var center = new Vector3f(0, 4, 0);
        assertEquals(center, NativeMonsterPresentation.deathTransform(20, 4).transformPosition(new Vector3f(center)));
        var top = NativeMonsterPresentation.deathTransform(20, 4).transformPosition(new Vector3f(0, 8, 0));
        assertEquals(-4, top.x, .00001F); assertEquals(4, top.y, .00001F);
        assertTrue(NativeMonsterPresentation.deathTransform(0, 4).equals(new org.joml.Matrix4f(), .00001F));
    }
    private RandomSource fixed(double value) {
        return new LegacyRandomSource(1) {
            @Override public double nextDouble() { return value; }
            @Override public float nextFloat() { return (float) value; }
            @Override public int nextInt(int bound) { return Math.min(bound - 1, (int)(value * bound)); }
        };
    }
    private List<SlimeLootRules.Drop> color(int rgb, int special, boolean first, double chance) {
        return SlimeLootRules.colorDrops(rgb, special, first, fixed(chance), fixed(chance));
    }
    private SlimeLootRules.Drop drop(String id, int count) { return new SlimeLootRules.Drop(id, count); }
    @Test void brownTakesPrecedenceOverBlackAndHasThreeToSixWood() {
        assertEquals(List.of(drop("388", 3), drop("709", 1)), color(0x46280A, 0, true, 0));
        assertEquals(List.of(drop("388", 6)), color(0x46280A, 0, true, .999));
    }
    @Test void blackUsesThePositionalRandomForBothMinerals() {
        assertEquals(List.of(drop("382", 1), drop("553", 1), drop("539", 1)),
                SlimeLootRules.colorDrops(0x202020, 0, true, fixed(.99), fixed(0)));
        assertEquals(List.of(drop("382", 1)), color(0x202020, 0, true, .05));
    }
    @Test void whiteColorParityAndGrayBranchesMatchSource() {
        assertEquals(List.of(drop("338", 1), drop("338", 1), drop("72", 1)), color(0xFFFFFF, 0, true, .99));
        assertEquals(List.of(drop("380", 1), drop("72", 1)), color(0xF0F0F0, 0, true, .99));
        assertEquals(List.of(drop("338", 1)), color(0xF1F0F0, 0, true, .99));
        assertEquals(List.of(drop("390", 2)), color(0xAAAAAA, 0, true, .99));
    }
    @Test void goldCopperAndPurpleKeepTheirOwnConditionsAndGenerationGates() {
        assertEquals(List.of(drop("384", 2)), color(0xE0CC20, 0, true, .99));
        assertEquals(List.of(drop("378", 2)), color(0xE07820, 0, true, .99));
        assertEquals(List.of(drop("386", 2), drop("485", 1)), color(0xC020F0, 4, true, 0));
        assertEquals(List.of(), color(0xC020F0, 2, true, 0));
        assertEquals(List.of(drop("386", 2)), color(0xC020F0, 2, false, 0));
        assertEquals(List.of(), color(0x55E01F, 4, true, 0));
    }
    @Test void sourceTablesPreserveRepeatedEntriesAndAllSpecies() throws Exception {
        try (var reader = resource("data/stardewcraft/monster_loot/source_tables.json")) {
            var tables = MonsterSourceLoot.decode(JsonParser.parseReader(reader).getAsJsonObject());
            assertEquals(51, tables.size());
            assertEquals(2, tables.get("Green Slime").drops().stream().filter(e -> e.item().equals("766")).count());
            assertTrue(MonsterSourceLoot.roll("Pepper Rex", fixed(0)).isEmpty());
            assertEquals(List.of("766", "766", "153", "66", "92", "96", "99"), MonsterSourceLoot.roll("Green Slime", fixed(0)));
            assertTrue(MonsterSourceLoot.roll("Green Slime", fixed(.999)).isEmpty());
        }
    }
    @Test void negativeDebrisIsModCoalOrGoldWithOneToThreePieces() {
        var items = MonsterSourceLoot.materialize(List.of("-4", "-6"), fixed(.999));
        assertEquals(2, items.size());
        assertEquals("stardewcraft:coal", BuiltInRegistries.ITEM.getKey(items.get(0).getItem()).toString());
        assertEquals("stardewcraft:gold_ore", BuiltInRegistries.ITEM.getKey(items.get(1).getItem()).toString());
        assertEquals(3, items.get(0).getCount()); assertEquals(3, items.get(1).getCount());
        assertEquals(1, MonsterSourceLoot.materialize(List.of("-4"), fixed(0)).getFirst().getCount());
    }
    @Test void sourceMaterialMappingFindsExistingItemsWithoutVanillaSubstitutes() {
        for (String id : List.of("766", "388", "709", "553", "539", "485", "107", "580", "583", "584", "221", "CalicoEgg")) {
            var stack = MonsterSourceLoot.item(id, 1);
            assertFalse(stack.isEmpty(), id);
            assertEquals("stardewcraft", BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace(), id);
        }
        assertTrue(MonsterSourceLoot.item("absent_monster_reward_for_test", 1).isEmpty());
    }
    @Test void locationBonusIsStoredWithoutEnteringBurglarBaseTable() {
        var id = net.minecraft.resources.ResourceLocation.parse("stardewcraft:slime_item");
        var bonus = net.minecraft.resources.ResourceLocation.parse("stardewcraft:calico_egg");
        var def = new MonsterDefinition(id, "Green Slime", "slime", 24, 5, 1, 0, 3, true, List.of(new MonsterDefinition.Drop(id, 1)));
        var state = new MonsterState(def, new MonsterSpawnContext(MonsterSpawnContext.Source.COMMAND, 1, false, null), fixed(0));
        state.addBornDrop(bonus); state.addBornDrop(bonus);
        var loaded = MonsterState.load(state.save());
        assertEquals(List.of(id.toString(), bonus.toString(), bonus.toString()), loaded.bornDrops());
        assertEquals(List.of(id.toString()), loaded.rerollDrops(fixed(0)));
    }
    @Test void festivalMonsterEggIncludesFirstSkullFloorAndCapsAtHalf() {
        assertEquals(0, DesertFestivalMineService.rollMonsterEggCount(120, 0, fixed(0)));
        assertEquals(1, DesertFestivalMineService.rollMonsterEggCount(121, 0, fixed(.021)));
        assertEquals(0, DesertFestivalMineService.rollMonsterEggCount(121, 0, fixed(.023)));
        assertEquals(0, DesertFestivalMineService.rollMonsterEggCount(10000, 100, fixed(.5)));
    }
}
