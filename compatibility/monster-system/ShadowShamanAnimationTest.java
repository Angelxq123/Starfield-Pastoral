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
class ShadowShamanAnimationTest {
    private NativeNpcModel model(){return new Gson().fromJson(new InputStreamReader(Objects.requireNonNull(getClass().getResourceAsStream("/shadow_shaman/model.json"))),NativeNpcModel.class);}
    @Test void clipsHaveClosedHullsAndNeverEnterTheFloor(){var m=model();var play=new NativeShadowShamanPlayback(m);float high=0;
        assertEquals(6,m.quads().stream().filter(q->q.sourcePart().equals("mask_outline")).count());assertEquals(6,m.quads().stream().filter(q->q.sourcePart().equals("body_outline")).count());
        for(int i=0;i<440;i++){double t=i*.01;play.sample(t,t>.3&&t<1.3,t>=1.3&&t<2.5,t-1.3,t-2.5,t-2.9,t>3.5?t-3.5:0);var matrices=play.pose().matrices();var v=new Vector3f();
            for(var q:m.quads())for(var p:q.vertices()){matrices[q.bone()].transformPosition(v.set(p[0],p[1],p[2]));assertTrue(v.y>=.179,"Geometry crosses shell margin at "+t);high=Math.max(high,v.y);
                if(t<=3.5&&q.sourcePart().contains("outline"))assertTrue(Math.abs(v.x)<7.04&&Math.abs(v.z)<7.04&&v.y<26.72,"Shell leaves collision box at "+t);}
            for(var matrix:matrices)assertTrue(matrix.isFinite()&&Math.abs(matrix.determinant())>.1);
        }assertTrue(high<34,"Unfolding stretches beyond the planned body height: "+high);
    }
    record Frame(String clip,double time,List<float[]> matrices,boolean parity){}
    @Test void exportProductionPoses()throws Exception{
        var m=model();var p=new NativeNpcPose(m);var out=new ArrayList<Frame>();
        // Use exact authored times; BB snaps within 1ms of a key while the native sampler interpolates continuously.
        for(var e:m.clips().entrySet())for(int i=0;i<=8;i++){double t=Math.round(e.getValue().length()*i/8*200)/200.;p.reset();p.apply(e.getKey(),t);out.add(new Frame(e.getKey(),t,Arrays.stream(p.matrices()).map(x->x.get(new float[16])).toList(),true));}
        var play=new NativeShadowShamanPlayback(m);
        for(int i=0;i<220;i++){double t=i*.02;play.sample(t,t>.3&&t<1.3,t>=1.3&&t<2.5,t-1.3,t-2.5,t-2.9,t>3.5?t-3.5:0);out.add(new Frame("sequence",t,Arrays.stream(play.pose().matrices()).map(x->x.get(new float[16])).toList(),false));}
        var target=Path.of("build/reports/shadow-shaman-animation");Files.createDirectories(target);Files.writeString(target.resolve("model.json"),new Gson().toJson(m));Files.writeString(target.resolve("poses.json"),new Gson().toJson(out));
    }
}
