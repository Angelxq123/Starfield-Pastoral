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
class ShadowBruteAnimationTest {
    private NativeNpcModel model(){return new Gson().fromJson(new InputStreamReader(Objects.requireNonNull(getClass().getResourceAsStream("/shadow_brute/model.json"))),NativeNpcModel.class);}
    @Test void jointsAndAperturesNeverEnterTheFloor(){var m=model();var play=new NativeShadowBrutePlayback(m);float high=0;
        assertEquals(.64,m.clips().get("animation.shadow_brute.walk").length(),1e-8);
        for(int i=0;i<440;i++){double t=i*.01;play.sample(t,t>.6&&t<2.4,t-2.7,t>3.5?t-3.5:0);var matrices=play.pose().matrices();var v=new Vector3f();
            for(var q:m.quads())for(var p:q.vertices()){matrices[q.bone()].transformPosition(v.set(p[0],p[1],p[2]));assertTrue(v.y>=.179,"Geometry crosses shell margin at "+t);if(t<=3.5&&q.sourcePart().equals("head"))assertTrue(Math.abs(v.x)<.43*16&&Math.abs(v.z)<.43*16,"Head exceeds living collision at "+t);high=Math.max(high,v.y);}
            for(var matrix:matrices)assertTrue(matrix.isFinite()&&Math.abs(matrix.determinant())>.1);
        }assertTrue(high<34,"Gait stretches beyond the planned body height: "+high);
    }
    @Test void bentHandsStayForwardAndStartStopRemainContinuous(){
        var m=model();var play=new NativeShadowBrutePlayback(m);List<Vector3f> previous=null;float largest=0;
        for(int i=0;i<600;i++){
            double t=i/200.;play.sample(t,t>.6&&t<1.9,-1,0);
            var pts=new ArrayList<Vector3f>();
            for(var q:m.quads())for(var v:q.vertices())pts.add(play.pose().matrices()[q.bone()].transformPosition(new Vector3f(v[0],v[1],v[2])));
            if(previous!=null)for(int j=0;j<pts.size();j++)largest=Math.max(largest,pts.get(j).distance(previous.get(j)));
            previous=pts;
            for(String side:List.of("left","right")){
                float x=side.equals("left")?-5:5;
                var elbow=play.pose().boneMatrix("arm_"+side).transformPosition(new Vector3f(x,11.98F,0));
                var hand=play.pose().boneMatrix("forearm_"+side).transformPosition(new Vector3f(x,12.96F,-3));
                assertTrue(hand.z<elbow.z-2,"Hooked hand must stay in front of its elbow: "+side);
            }
        }
        assertTrue(largest<.65,"Start/stop produces a visible pose jump: "+largest);
    }
    record Frame(String clip,double time,List<float[]> matrices,boolean parity){}
    @Test void exportProductionPoses()throws Exception{
        var m=model();var p=new NativeNpcPose(m);var out=new ArrayList<Frame>();
        // Use exact authored times; BB snaps within 1ms of a key while the native sampler interpolates continuously.
        for(var e:m.clips().entrySet())for(int i=0;i<=8;i++){double t=Math.round(e.getValue().length()*i/8*200)/200.;p.reset();p.apply(e.getKey(),t);out.add(new Frame(e.getKey(),t,Arrays.stream(p.matrices()).map(x->x.get(new float[16])).toList(),true));}
        var play=new NativeShadowBrutePlayback(m);
        for(int i=0;i<220;i++){double t=i*.02;play.sample(t,t>.6&&t<2.4,t-2.7,t>3.5?t-3.5:0);out.add(new Frame("sequence",t,Arrays.stream(play.pose().matrices()).map(x->x.get(new float[16])).toList(),false));}
        var target=Path.of("build/reports/shadow-brute-animation");Files.createDirectories(target);Files.writeString(target.resolve("model.json"),new Gson().toJson(m));Files.writeString(target.resolve("poses.json"),new Gson().toJson(out));
    }
}
