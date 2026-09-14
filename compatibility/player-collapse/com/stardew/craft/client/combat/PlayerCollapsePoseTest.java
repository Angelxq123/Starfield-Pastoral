package com.stardew.craft.client.combat;

import com.google.gson.*;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PlayerCollapsePoseTest {
    static PlayerModel<?> model(boolean slim) {
        return new CollapsePlayerModel<>(slim);
    }
    static List<float[]> vertices(PlayerModel<?> model,CombatCollapsePose.Frame frame,float yaw) {
        CombatCollapseModelPose.apply(model,frame);
        PoseStack stack=new PoseStack();
        stack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(yaw));
        CombatCollapseModelPose.root(stack,model,frame,.9375f);
        stack.scale(-.9375f,-.9375f,.9375f);stack.translate(0,-1.501,0);
        var vertices=new Vertices();model.renderToBuffer(stack,vertices,0,0);return vertices.values;
    }
    @Test void actualWideAndSlimSkinVerticesStaySupportedAndContinuous() {
        for(boolean slim:new boolean[]{false,true}) for(float yaw:new float[]{0,90,180,270}) {
            var model=model(slim);
            for(boolean recovery:new boolean[]{false,true}) {
                List<float[]> previous=null;
                for(float tick=0;tick<= (recovery?44:24);tick+=.05f) {
                    var frame=recovery?CombatCollapsePose.recover(tick):CombatCollapsePose.fall(tick);
                    var points=vertices(model,frame,yaw);
                    float min=Float.POSITIVE_INFINITY;
                    for(int i=0;i<points.size();i++) {
                        var p=points.get(i);min=Math.min(min,p[1]);
                        for(float value:p)assertTrue(Float.isFinite(value));
                        assertTrue(p[1]>=-.032,"skin penetrated ground: "+p[1]);
                        if(previous!=null)for(int axis=0;axis<3;axis++)
                            assertTrue(Math.abs(p[axis]-previous.get(i)[axis])<.04,"discontinuous skin vertex at "+tick);
                    }
                    assertTrue(min<.02,"floating skin support: "+min);
                    previous=points;
                }
            }
        }
    }
    @Test void adultProportionsMatchTheVanillaPlayer() {
        for(boolean slim:new boolean[]{false,true}) {
            var articulated=model(slim);
            assertFalse(articulated.young,"Player must never use baby body scaling");
            var vanilla=new PlayerModel<>(PlayerModel.createMesh(CubeDeformation.NONE,slim).getRoot().bake(64,64),slim);
            vanilla.young=false;
            var actual=vertices(articulated,CombatCollapsePose.STANDING,0);
            var expected=vertices(vanilla,CombatCollapsePose.STANDING,0);
            for(int axis=0;axis<3;axis++) {
                final int a=axis;
                assertEquals(expected.stream().mapToDouble(v->v[a]).min().orElseThrow(),actual.stream().mapToDouble(v->v[a]).min().orElseThrow(),.00001);
                assertEquals(expected.stream().mapToDouble(v->v[a]).max().orElseThrow(),actual.stream().mapToDouble(v->v[a]).max().orElseThrow(),.00001);
            }
            double height=actual.stream().mapToDouble(v->v[1]).max().orElseThrow()-actual.stream().mapToDouble(v->v[1]).min().orElseThrow();
            assertTrue(height>1.8 && height<2,"Unexpected player height "+height);
        }
    }
    @Test void playerRendererAndArmorMixinsLoad() throws Exception {
        Class.forName("net.minecraft.client.renderer.entity.player.PlayerRenderer");
        Class.forName("net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer");
    }
    @Test void armorJointsFollowSkinAndSupportTheFloor() {
        var player=new CollapsePlayerModel<>(false);
        for(boolean inner:new boolean[]{false,true}) {
            var armor=new CollapseArmorModel(inner);
            for(float tick=0;tick<=44;tick+=.5f) {
                var frame=CombatCollapsePose.recover(tick);
                CombatCollapseModelPose.apply(player,frame);
                armor.setAllVisible(true);armor.follow(player,armor);
                assertEquals(player.leftLeg.getChild("bend").xRot,armor.leftLeg.getChild("bend").xRot);
                assertEquals(player.rightArm.getChild("bend").xRot,armor.rightArm.getChild("bend").xRot);
                assertTrue(Float.isFinite(CombatCollapseModelPose.floorLift(armor,frame,.9375f)));
            }
        }
    }
    @Test void endpointAndHandoffAreIdentical() {
        assertEquals(CombatCollapsePose.PRONE,CombatCollapsePose.fall(24));
        assertEquals(CombatCollapsePose.PRONE,CombatCollapsePose.recover(0));
        assertEquals(CombatCollapsePose.STANDING,CombatCollapsePose.recover(44));
        assertFalse(CombatCollapseTimeline.shouldAcknowledge(59));
        assertTrue(CombatCollapseTimeline.shouldAcknowledge(60));
        assertEquals(1,CombatCollapseTimeline.blackAlpha(60,0));
    }
    @Test void rescueCoordinatesMatchShippedLobbyAndEvent() throws Exception {
        Path project=Path.of(System.getProperty("stardewcraft.projectDir"));
        Path root=project.resolve("src/main/resources/data/stardewcraft");
        var meta=JsonParser.parseString(Files.readString(root.resolve("mine_layouts/earth_lobby.json"))).getAsJsonObject();
        var event=JsonParser.parseString(Files.readString(root.resolve("cutscene_events/combat_rescue_mine.json"))).getAsJsonObject();
        double[][] expected={{1.5,66,-1.5},{.5,66,-1.5},{.5,66,1.5},{3.5,69,.5}};
        String pointsSource=Files.readString(project.resolve("src/main/java/com/stardew/craft/cutscene/server/CombatRescuePoints.java"));
        String[] declarations={"1.5D, 66.0D, -1.5D","0.5D, 66.0D, -1.5D","0.5D, 66.0D, 1.5D","3.5D, 69.0D, 0.5D"};
        for(String declaration:declarations)assertTrue(pointsSource.contains(declaration));
        int[] index={0};
        for(var entry:event.getAsJsonArray("commands")) {
            var command=entry.getAsJsonObject();String kind=command.get("cmd").getAsString();
            if(!Set.of("spawn_actor","move_actor","camera").contains(kind))continue;
            int point=kind.equals("camera")?3:kind.equals("move_actor")?2:index[0]++;
            for(int axis=0;axis<3;axis++)assertEquals(expected[point][axis],command.get(new String[]{"x","y","z"}[axis]).getAsDouble());
        }
        // SDV farmer 19,10; rescuer walks from 18,10 through 18,13. Camera occupies 21,12.
        for(int[] tile:new int[][]{{19,9},{19,10},{19,11},{18,10},{18,11},{18,12},{18,13},{21,12}}) {
            boolean open=false;
            for(var c:meta.getAsJsonArray("cells")) {
                var cell=c.getAsJsonObject();var t=cell.getAsJsonArray("tile");
                if(t.get(0).getAsInt()==tile[0]&&t.get(1).getAsInt()==tile[1])open=cell.get("ground_open").getAsBoolean();
            }
            assertTrue(open,"blocked rescue tile "+Arrays.toString(tile));
        }
        String coordinator=Files.readString(project.resolve("src/main/java/com/stardew/craft/cutscene/server/CombatRescueCutsceneCoordinator.java"));
        assertTrue(coordinator.indexOf("OrdinaryMineRuntime.ensure(targetLevel, 0)")<coordinator.indexOf("ModTeleport.to("));
    }
    @Test void exportActualPlaybackWhenRequested() throws Exception {
        String target=System.getProperty("collapse.preview");if(target==null)return;
        var model=model(false);var frames=new ArrayList<List<float[]>>();
        // Actual production matrices, 40 fps: collapse, held prone, recovery, held standing.
        for(int i=0;i<240;i++) {
            float t=i*.5f;
            var pose=t<24?CombatCollapsePose.fall(t):t<55?CombatCollapsePose.PRONE:CombatCollapsePose.recover(t-55);
            frames.add(vertices(model,pose,0));
        }
        Files.writeString(Path.of(target),new Gson().toJson(Map.of("fps",40,"frames",frames)));
    }
    static class Vertices implements VertexConsumer {
        final List<float[]> values=new ArrayList<>();
        public VertexConsumer addVertex(float x,float y,float z) {values.add(new float[]{x,y,z,0,0});return this;}
        public VertexConsumer setColor(int r,int g,int b,int a){return this;}
        public VertexConsumer setUv(float u,float v){var p=values.getLast();p[3]=u;p[4]=v;return this;}
        public VertexConsumer setUv1(int u,int v){return this;}
        public VertexConsumer setUv2(int u,int v){return this;}
        public VertexConsumer setNormal(float x,float y,float z){return this;}
    }
}
