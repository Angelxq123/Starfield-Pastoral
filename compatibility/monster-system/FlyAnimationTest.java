package com.stardew.craft.monster;

import com.google.gson.Gson;
import com.stardew.craft.client.monsternative.*;
import com.stardew.craft.client.npcnative.*;
import org.junit.jupiter.api.Test;
import org.joml.Vector3f;
import java.io.InputStreamReader;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class FlyAnimationTest {
    private NativeNpcModel model() {return new Gson().fromJson(new InputStreamReader(Objects.requireNonNull(getClass().getResourceAsStream("/fly/model.json"))),NativeNpcModel.class);}
    @Test void wingsStaySeparateAndEmergenceKeepsSourceScale() {
        var m=model();assertEquals(4,m.clips().size());assertEquals(4,m.quads().stream().filter(NativeNpcModel.Quad::translucent).count());
        var p=new NativeNpcPose(m);var v=new Vector3f();
        for(int i=0;i<=250;i++) {
            double t=i/200.;NativeFlyMotion.flight(p,t,t,2);
            assertEquals(Math.clamp(t,.2,1),p.matrices()[0].m00(),1e-6);
            for(var q:m.quads())for(var a:q.vertices())assertTrue(p.matrices()[q.bone()].transformPosition(v.set(a[0],a[1],a[2])).y>0);
        }
        for(var c:m.clips().values())if(c.loop())for(var t:c.tracks())assertArrayEquals(t.keys().getFirst().after(),t.keys().getLast().before());
    }
    @Test void deathAndHitCanInterruptAnyWingPhase() {
        var m=model();var p=new NativeNpcPose(m);var captured=new NativeNpcPose(m);var v=new Vector3f();
        for(double t:new double[]{.03,.095,.18,.257}) {
            NativeFlyMotion.flight(captured,t,1,2);NativeFlyMotion.flight(p,t,1,0);
            for(int b=0;b<m.bones().size();b++)assertTrue(p.matrices()[b].equals(captured.matrices()[b],1e-5F));
            NativeFlyMotion.death(m,p,captured,0);
            for(int b=0;b<m.bones().size();b++)assertTrue(p.matrices()[b].equals(captured.matrices()[b],1e-5F));
            for(int i=1;i<=150;i++) {
                NativeFlyMotion.death(m,p,captured,i*.004);
                for(var q:m.quads())for(var a:q.vertices())assertTrue(p.matrices()[q.bone()].transformPosition(v.set(a[0],a[1],a[2])).y>=.249F);
            }
        }
    }
    record Frame(String clip,double time,List<float[]> matrices,boolean parity) {}
    @Test void exportProductionPoses() throws Exception {
        var m=model();var p=new NativeNpcPose(m);var out=new ArrayList<Frame>();
        for(var e:m.clips().entrySet())for(int i=0;i<=8;i++) {
            double t=e.getValue().length()*i/8;p.reset();p.apply(e.getKey(),t);out.add(new Frame(e.getKey(),t,Arrays.stream(p.matrices()).map(x->x.get(new float[16])).toList(),true));
        }
        var playback=new NativeFlyPlayback(m);
        for(int i=0;i<160;i++) {
            double t=i*.02;playback.sample(t,t,t-1.4,t>2.5?t-2.5:0);out.add(new Frame("sequence",t,Arrays.stream(playback.pose().matrices()).map(x->x.get(new float[16])).toList(),false));
        }
        var target=Path.of("build/reports/fly-animation");Files.createDirectories(target);
        Files.writeString(target.resolve("model.json"),new Gson().toJson(m));Files.writeString(target.resolve("poses.json"),new Gson().toJson(out));
    }
}
