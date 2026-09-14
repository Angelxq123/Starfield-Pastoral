package com.stardew.craft.monster;

import com.google.gson.Gson;
import com.stardew.craft.client.monsternative.NativeRockCrabMotion;
import com.stardew.craft.client.npcnative.NativeNpcModel;
import com.stardew.craft.client.npcnative.NativeNpcPose;
import org.junit.jupiter.api.Test;
import org.joml.Vector3f;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class RockCrabAnimationTest {
    private NativeNpcModel model() {
        return new Gson().fromJson(new InputStreamReader(Objects.requireNonNull(getClass().getResourceAsStream("/rock_crab/model.json"))), NativeNpcModel.class);
    }
    @Test void loopsAndStateJoinsKeepCompletePose() {
        var m=model();assertEquals(15,m.clips().size());
        for(var clip:m.clips().values()) for(var track:clip.tracks()) {
            assertNotEquals("scale",track.channel());
            if(clip.loop()) assertArrayEquals(track.keys().getFirst().after(),track.keys().getLast().before());
        }
        var a=new NativeNpcPose(m);var b=new NativeNpcPose(m);
        String[][] joins={{"disguise","emerge"},{"emerge","idle"},{"idle","walk_start"},{"walk_start","walk"},{"walk","walk_stop"},{"walk_stop","retract"},{"retract","disguise"},{"bare_idle","flee_start"},{"flee_start","flee"},{"flee","flee_stop"},{"flee_stop","bare_idle"},{"shell_hit","idle"}};
        for(var pair:joins) {
            NativeRockCrabMotion.sample(m,a,pair[0],m.clips().get("animation.rock_crab."+pair[0]).length());
            NativeRockCrabMotion.sample(m,b,pair[1],0);
            for(int i=0;i<m.bones().size();i++)assertTrue(a.matrices()[i].equals(b.matrices()[i],.0001F),Arrays.toString(pair)+m.bones().get(i).name());
        }
        assertTrue(m.quads().stream().filter(q->NativeRockCrabMotion.visible(q.sourcePart(),"disguise",.5)).allMatch(q->q.sourcePart().startsWith("shell_")));
        assertFalse(NativeRockCrabMotion.visible("shell_main","shell_break",.32));
        assertFalse(NativeRockCrabMotion.visible("shell_main","flee",.1));
    }
    @Test void movingAndDyingKeepHullOffGroundAndTripodSupports() {
        var m=model();var pose=new NativeNpcPose(m);var v=new Vector3f();
        for(var entry:m.clips().entrySet()) {
            String name=entry.getKey().substring("animation.rock_crab.".length());
            for(int i=0;i<=240;i++) {
                double time=entry.getValue().length()*i/240;
                NativeRockCrabMotion.sample(m,pose,name,time);var matrices=pose.matrices();
                for(var q:m.quads())if(q.sourcePart().equals("body_outline")&&NativeRockCrabMotion.visible(q.sourcePart(),name,time)) {
                    for(var p:q.vertices())assertTrue(matrices[q.bone()].transformPosition(v.set(p[0],p[1],p[2])).y>=.199F,name+" hull intersects ground");
                }
                if(name.equals("walk")||name.equals("flee")) {
                    int planted=0;
                    for(var side:List.of("left","right"))for(int leg=0;leg<3;leg++) {
                        float low=Float.POSITIVE_INFINITY;String part="leg_lower_"+side+"_"+leg;
                        for(var q:m.quads())if(q.sourcePart().equals(part))for(var p:q.vertices())low=Math.min(low,matrices[q.bone()].transformPosition(v.set(p[0],p[1],p[2])).y);
                        assertTrue(low>=0,name+" foot underground");if(low<.45)planted++;
                    }
                    assertTrue(planted>=3,name+" fewer than three supporting legs");
                }
            }
        }
    }
    @Test void arbitraryWalkStopHasNoFirstFrameSnap() {
        var m=model();var source=new NativeNpcPose(m);var result=new NativeNpcPose(m);
        for(double time:new double[]{.031,.12,.29,.42}) {
            NativeRockCrabMotion.sample(m,source,"walk",time);
            NativeRockCrabMotion.stopFrom(result,source,0);
            for(int i=0;i<m.bones().size();i++)assertTrue(source.matrices()[i].equals(result.matrices()[i],.00001F));
            NativeRockCrabMotion.stopFrom(result,source,1);var rest=new NativeNpcPose(m);
            for(int i=0;i<m.bones().size();i++)assertTrue(rest.matrices()[i].equals(result.matrices()[i],.00001F));
        }
    }
    record Frame(String clip,double time,List<float[]> matrices,List<String> hidden,boolean parity) {}
    @Test void exportActualNativeMatricesForEditorComparisonAndPreview() throws Exception {
        var m=model();var pose=new NativeNpcPose(m);var frames=new ArrayList<Frame>();
        for(var entry:m.clips().entrySet()) {
            String name=entry.getKey().substring("animation.rock_crab.".length());
            int count=(int)Math.ceil(entry.getValue().length()*40);
            for(int i=0;i<count;i++) {
                double t=i*entry.getValue().length()/count;
                NativeRockCrabMotion.sample(m,pose,name,t);
                List<float[]> transforms=Arrays.stream(pose.matrices()).map(x->x.get(new float[16])).toList();
                var hidden=m.quads().stream().map(NativeNpcModel.Quad::sourcePart).distinct().filter(p->!NativeRockCrabMotion.visible(p,name,t)).toList();
                frames.add(new Frame(name,t,transforms,hidden,!name.startsWith("death_")));
            }
        }
        var output=Path.of("build/reports/rock-crab-animation");Files.createDirectories(output);
        Files.writeString(output.resolve("poses.json"),new Gson().toJson(frames));
        Files.writeString(output.resolve("model.json"),new Gson().toJson(m));
    }
}
