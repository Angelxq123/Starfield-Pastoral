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
class RockGolemAnimationTest {
    private NativeNpcModel model(){return new Gson().fromJson(new InputStreamReader(Objects.requireNonNull(getClass().getResourceAsStream("/rock_golem/model.json"))),NativeNpcModel.class);}
    @Test void eightSourceFramesWakeOnceAndDamageFocusSurvivesSave(){var a=new RockGolemAwakening();for(int i=0;i<100;i++)assertFalse(a.step(false));assertFalse(a.seen());assertTrue(a.step(true));assertFalse(a.walking());int n=0;while(!a.awake()){assertFalse(a.step(true));assertTrue(++n<50);}assertEquals(40,n);assertTrue(a.walking());a.struck();var b=new RockGolemAwakening();b.load(a.save());assertEquals(a.save(),b.save());assertTrue(b.focused());}
    @Test void clipsHaveClosedHullsAndNeverEnterTheFloor(){var m=model();var play=new NativeRockGolemPlayback(m);float high=0;
        assertEquals(6,m.quads().stream().filter(q->q.sourcePart().equals("head_outline")).count());assertEquals(6,m.quads().stream().filter(q->q.sourcePart().equals("torso_outline")).count());
        for(int i=0;i<440;i++){double t=i*.01;int phase=t<.6?0:t<1.2666667?1:2;double progress=phase==0?0:phase==2?1:(t-.6)/(.6666667);play.sample(t,t>1.4&&t<2.4,phase,progress,t-2.7,t>3.5?t-3.5:0);var matrices=play.pose().matrices();var v=new Vector3f();
            for(var q:m.quads())for(var p:q.vertices()){matrices[q.bone()].transformPosition(v.set(p[0],p[1],p[2]));assertTrue(v.y>=.199,"Geometry crosses shell margin at "+t);high=Math.max(high,v.y);}
            for(var matrix:matrices)assertTrue(matrix.isFinite()&&Math.abs(matrix.determinant())>.1);
        }assertTrue(high<27,"Unfolding stretches beyond the planned body height: "+high);
    }
    @Test void bothArmsStayForwardThroughoutTheGait(){
        var m=model();var pose=new NativeNpcPose(m);
        for(int i=0;i<=58;i++){
            pose.reset();pose.apply("animation.rock_golem.walk",i*.01);
            for(String side:List.of("left","right")){
                var hand=pose.boneMatrix("arm_"+side).transformPosition(new Vector3f(side.equals("left")?-4:4,7,0));
                assertTrue(hand.z < -4, "Source golem keeps both hands forward: "+side+" "+hand);
            }
        }
        for(String side:List.of("left","right"))assertEquals(6,m.quads().stream().filter(q->q.sourcePart().equals("eyes_"+side+"_outline")).count());
    }
    record Frame(String clip,double time,List<float[]> matrices,boolean parity){}
    @Test void exportProductionPoses()throws Exception{
        var m=model();var p=new NativeNpcPose(m);var out=new ArrayList<Frame>();
        // Use exact authored times; BB snaps within 1ms of a key while the native sampler interpolates continuously.
        for(var e:m.clips().entrySet())for(int i=0;i<=8;i++){double t=Math.round(e.getValue().length()*i/8*200)/200.;p.reset();p.apply(e.getKey(),t);out.add(new Frame(e.getKey(),t,Arrays.stream(p.matrices()).map(x->x.get(new float[16])).toList(),true));}
        var play=new NativeRockGolemPlayback(m);
        for(int i=0;i<220;i++){double t=i*.02;int phase=t<.6?0:t<1.2666667?1:2;double progress=phase==0?0:phase==2?1:(t-.6)/.6666667;play.sample(t,t>1.4&&t<2.4,phase,progress,t-2.7,t>3.5?t-3.5:0);out.add(new Frame("sequence",t,Arrays.stream(play.pose().matrices()).map(x->x.get(new float[16])).toList(),false));}
        var target=Path.of("build/reports/rock-golem-animation");Files.createDirectories(target);Files.writeString(target.resolve("model.json"),new Gson().toJson(m));Files.writeString(target.resolve("poses.json"),new Gson().toJson(out));
    }
}
