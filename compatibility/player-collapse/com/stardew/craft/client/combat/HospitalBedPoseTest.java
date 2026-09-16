package com.stardew.craft.client.combat;

import com.google.gson.*;
import com.mojang.blaze3d.vertex.PoseStack;
import org.junit.jupiter.api.Test;
import org.joml.Vector3d;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class HospitalBedPoseTest {
    private static List<float[]> vertices(CollapsePlayerModel<?> model,float tick) {
        return vertices(model,tick,0);
    }
    private static List<float[]> vertices(CollapsePlayerModel<?> model,float tick,int armorType) {
        PoseStack stack=new PoseStack();
        HospitalBedPose.root(stack,model,HospitalBedPose.sample(tick,armorType!=0));
        stack.scale(-.9375f,-.9375f,.9375f);stack.translate(0,-1.501,0);
        var vertices=new PlayerCollapsePoseTest.Vertices();
        if(armorType==0)model.renderToBuffer(stack,vertices,0,0);
        else {
            var armor=new CollapseArmorModel(armorType==1);armor.setAllVisible(true);armor.follow(model,armor);
            armor.renderToBuffer(stack,vertices,0,0);
        }
        return vertices.values;
    }
    @Test void adultBedMotionStaysContinuousAndFinishesAtTheRealPlayer() {
        for(boolean slim:new boolean[]{false,true}) {
            var model=new CollapsePlayerModel<>(slim);List<float[]> previous=null;
            for(float tick=0;tick<=104;tick+=.05f) {
                var points=vertices(model,tick);
                for(int i=0;i<points.size();i++) {
                    for(int a=0;a<3;a++) {
                        assertTrue(Float.isFinite(points.get(i)[a]));
                        if(previous!=null)assertTrue(Math.abs(points.get(i)[a]-previous.get(i)[a])<.025,"Bed pose discontinuity at "+tick);
                    }
                    assertTrue(points.get(i)[1]>-.04,"Below room floor");
                }
                previous=points;
            }
            var resting=vertices(model,-1);var start=vertices(model,0);
            for(int i=0;i<resting.size();i++)assertArrayEquals(resting.get(i),start.get(i),.000001f);
            var end=vertices(model,104);
            var standing=PlayerCollapsePoseTest.vertices(model,CombatCollapsePose.STANDING,270);
            for(int i=0;i<end.size();i++)assertArrayEquals(standing.get(i),end.get(i),.00001f);
        }
    }
    @Test void skinSurfacesAvoidTheActualBedModel() throws Exception {
        var project=Path.of(System.getProperty("stardewcraft.projectDir"));
        var json=JsonParser.parseString(Files.readString(project.resolve("src/main/resources/assets/stardewcraft/models/decor/common/bed_1.json"))).getAsJsonObject();
        for(boolean slim:new boolean[]{false,true}) {
            var model=new CollapsePlayerModel<>(slim);
            for(int armorType=0;armorType<=2;armorType++)for(float tick=-1;tick<=104;tick+=1) {
                var points=vertices(model,tick,armorType);
                for(int face=0;face<points.size();face+=4) {
                    // Surface grid catches limbs crossing a rail between corner vertices.
                    for(int u=0;u<=3;u++)for(int v=0;v<=3;v++) {
                        var a=points.get(face);var b=points.get(face+1);var c=points.get(face+3);
                        double x=a[0]+(b[0]-a[0])*u/3+(c[0]-a[0])*v/3+23.65;
                        double y=a[1]+(b[1]-a[1])*u/3+(c[1]-a[1])*v/3+43;
                        double z=a[2]+(b[2]-a[2])*u/3+(c[2]-a[2])*v/3-14.625;
                        Vector3d local=new Vector3d(16-(x-22)*16,(y-43)*16,16-(z+15)*16);
                        for(var entry:json.getAsJsonArray("elements")) {
                            var e=entry.getAsJsonObject();Vector3d p=new Vector3d(local);
                            if(e.has("rotation")) {
                                var r=e.getAsJsonObject("rotation");var o=r.getAsJsonArray("origin");
                                Vector3d origin=new Vector3d(o.get(0).getAsDouble(),o.get(1).getAsDouble(),o.get(2).getAsDouble());
                                p.sub(origin);double angle=-Math.toRadians(r.get("angle").getAsDouble());
                                switch(r.get("axis").getAsString()){case "x"->p.rotateX(angle);case "y"->p.rotateY(angle);case "z"->p.rotateZ(angle);}
                                p.add(origin);
                            }
                            var from=e.getAsJsonArray("from");var to=e.getAsJsonArray("to");boolean inside=true;
                            for(int axis=0;axis<3;axis++)inside &= p.get(axis)>from.get(axis).getAsDouble()+.18 && p.get(axis)<to.get(axis).getAsDouble()-.18;
                            assertFalse(inside,"Bed intersection armor="+armorType+" tick="+tick+" face="+face+" point="+p+" element="+from+".."+to);
                        }
                    }
                }
            }
        }
    }
    @Test void exportBedPreviewWhenRequested() throws Exception {
        String target=System.getProperty("hospital.preview");if(target==null)return;
        var model=new CollapsePlayerModel<>(false);var frames=new ArrayList<List<float[]>>();
        for(int i=0;i<304;i++) {float time=i*.5f-24;frames.add(vertices(model,time<0?-1:time));}
        Files.writeString(Path.of(target),new Gson().toJson(Map.of("fps",40,"frames",frames)));
    }
}
