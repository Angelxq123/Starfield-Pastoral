package com.stardew.craft.monster;

import com.google.gson.Gson;
import com.stardew.craft.client.monsternative.NativeBugMotion;
import com.stardew.craft.client.npcnative.NativeNpcModel;
import com.stardew.craft.client.npcnative.NativeNpcPose;
import org.junit.jupiter.api.Test;
import org.joml.Vector3f;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class BugAnimationTest {
    private NativeNpcModel model() {
        return new Gson().fromJson(new InputStreamReader(Objects.requireNonNull(
                getClass().getResourceAsStream("/bug/model.json"))), NativeNpcModel.class);
    }
    @Test void independentLayersPreserveHardCoversAndLoopSeams() {
        var m=model();assertEquals(4,m.clips().size());
        assertEquals(4,m.quads().stream().filter(NativeNpcModel.Quad::translucent).count());
        for(var clip:m.clips().values())for(var track:clip.tracks()) {
            assertNotEquals("scale",track.channel());
            if(clip.loop())assertArrayEquals(track.keys().getFirst().after(),track.keys().getLast().before());
        }
        var rest=new NativeNpcPose(m);var p=new NativeNpcPose(m);
        for(int i=0;i<160;i++) {
            p.reset();p.apply("animation.bug.fly",i/1000.);
            for(int b=0;b<m.bones().size();b++)if(m.bones().get(b).name().startsWith("cover_"))
                assertTrue(p.matrices()[b].equals(rest.matrices()[b],1e-6F));
        }
        NativeBugMotion.flight(p,.037,.25);assertEquals(2.5,p.matrices()[0].m30(),1e-6);
        NativeBugMotion.flight(p,.037,.75);assertEquals(-2.5,p.matrices()[0].m30(),1e-6);
    }
    @Test void reactionsJoinArbitraryFlightPhasesAndNeverCrossFloor() {
        var m=model();var captured=new NativeNpcPose(m);var p=new NativeNpcPose(m);var v=new Vector3f();
        for(double phase:new double[]{.017,.04,.093,.12}) {
            NativeBugMotion.flight(captured,phase,.231);
            NativeBugMotion.death(m,p,captured,0);
            for(int b=0;b<m.bones().size();b++)assertTrue(p.matrices()[b].equals(captured.matrices()[b],1e-5F));
            for(int i=0;i<=240;i++) {
                NativeBugMotion.death(m,p,captured,i*.6/240);
                assertEquals(captured.matrices()[0].m30(),p.matrices()[0].m30(),1e-5);
                for(var q:m.quads())for(var vertex:q.vertices())assertTrue(p.matrices()[q.bone()]
                        .transformPosition(v.set(vertex[0],vertex[1],vertex[2])).y>=.249F);
            }
            NativeBugMotion.flight(p,phase,.231);NativeBugMotion.hit(p,0);
            for(int b=0;b<m.bones().size();b++)assertTrue(p.matrices()[b].equals(captured.matrices()[b],1e-5F));
            NativeBugMotion.hit(p,.24);
            for(int b=0;b<m.bones().size();b++)assertTrue(p.matrices()[b].equals(captured.matrices()[b],1e-5F));
        }
        for(int i=0;i<800;i++) {
            NativeBugMotion.flight(p,i*.005,i*.005);
            for(var q:m.quads())for(var vertex:q.vertices())assertTrue(p.matrices()[q.bone()]
                    .transformPosition(v.set(vertex[0],vertex[1],vertex[2])).y>.25F,"flight geometry underground");
        }
    }
    record Frame(String clip,double time,List<float[]> matrices,boolean parity) {}
    private Frame frame(NativeNpcPose p,String clip,double time,boolean parity) {
        return new Frame(clip,time,Arrays.stream(p.matrices()).map(x->x.get(new float[16])).toList(),parity);
    }
    @Test void exportProductionMatricesForIndependentEditorComparison() throws Exception {
        var m=model();var p=new NativeNpcPose(m);var frames=new ArrayList<Frame>();
        for(var e:m.clips().entrySet())for(int i=0;i<=12;i++) {
            double t=e.getValue().length()*i/12;p.reset();p.apply(e.getKey(),t);
            frames.add(frame(p,e.getKey(),t,true));
        }
        for(int i=0;i<200;i++) {
            double t=i*.02;NativeBugMotion.flight(p,t,t);frames.add(frame(p,"flight",t,false));
        }
        // One continuous flight -> hit -> recovery -> death sequence, sampled at 50 fps.
        var captured=new NativeNpcPose(m);
        NativeBugMotion.flight(captured,1.38,1.38);
        for(int i=0;i<114;i++) {
            double t=i*.02;
            if(t<1.38) {
                NativeBugMotion.flight(p,t,t);
                if(t>=.6&&t<=.84)NativeBugMotion.hit(p,t-.6);
            } else NativeBugMotion.death(m,p,captured,t-1.38);
            frames.add(frame(p,"reaction",t,false));
        }
        var output=Path.of("build/reports/bug-animation");Files.createDirectories(output);
        Files.writeString(output.resolve("poses.json"),new Gson().toJson(frames));
        Files.writeString(output.resolve("model.json"),new Gson().toJson(m));
    }
}
