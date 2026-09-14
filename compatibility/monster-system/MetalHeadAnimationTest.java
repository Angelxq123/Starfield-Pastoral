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
class MetalHeadAnimationTest {
    private NativeNpcModel model(){return new Gson().fromJson(new InputStreamReader(Objects.requireNonNull(getClass().getResourceAsStream("/metal_head/model.json"))),NativeNpcModel.class);}
    @Test void helmetUsesKillCountAndUncheckedSaveIdentity(){assertFalse(MetalHeadLoot.hasHelmet(62,37));assertTrue(MetalHeadLoot.hasHelmet(63,37));assertTrue(MetalHeadLoot.hasHelmet(163,37));assertFalse(MetalHeadLoot.hasHelmet(64,37));assertEquals((10+(int)Long.MAX_VALUE)%100==0,MetalHeadLoot.hasHelmet(10,Long.MAX_VALUE));}
    @Test void clipsHaveClosedHullsAndNeverEnterTheFloor(){var m=model();var play=new NativeMetalHeadPlayback(m);float high=0;
        assertEquals(6,m.quads().stream().filter(q->q.sourcePart().equals("helmet_outline")).count());
        for(int i=0;i<440;i++){double t=i*.01;play.sample(t,t>.6&&t<2.4,t-2.7,t>3.5?t-3.5:0);var matrices=play.pose().matrices();var v=new Vector3f();
            for(var q:m.quads())for(var p:q.vertices()){matrices[q.bone()].transformPosition(v.set(p[0],p[1],p[2]));assertTrue(v.y>=.179,"Geometry crosses shell margin at "+t);high=Math.max(high,v.y);}
            for(var matrix:matrices)assertTrue(matrix.isFinite()&&Math.abs(matrix.determinant())>.1);
        }assertTrue(high<19,"Unfolding stretches beyond the planned body height: "+high);
    }
    record Frame(String clip,double time,List<float[]> matrices,boolean parity){}
    @Test void exportProductionPoses()throws Exception{
        var m=model();var p=new NativeNpcPose(m);var out=new ArrayList<Frame>();
        // Use exact authored times; BB snaps within 1ms of a key while the native sampler interpolates continuously.
        for(var e:m.clips().entrySet())for(int i=0;i<=8;i++){double t=Math.round(e.getValue().length()*i/8*200)/200.;p.reset();p.apply(e.getKey(),t);out.add(new Frame(e.getKey(),t,Arrays.stream(p.matrices()).map(x->x.get(new float[16])).toList(),true));}
        var play=new NativeMetalHeadPlayback(m);
        for(int i=0;i<220;i++){double t=i*.02;play.sample(t,t>.6&&t<2.4,t-2.7,t>3.5?t-3.5:0);out.add(new Frame("sequence",t,Arrays.stream(play.pose().matrices()).map(x->x.get(new float[16])).toList(),false));}
        var target=Path.of("build/reports/metal-head-animation");Files.createDirectories(target);Files.writeString(target.resolve("model.json"),new Gson().toJson(m));Files.writeString(target.resolve("poses.json"),new Gson().toJson(out));
    }
}
