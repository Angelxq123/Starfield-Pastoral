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
class PepperRexAnimationTest {
 private NativeNpcModel model(){return new Gson().fromJson(new InputStreamReader(Objects.requireNonNull(getClass().getResourceAsStream("/pepper_rex/model.json"))),NativeNpcModel.class);}
 record Frame(String clip,double time,List<float[]> matrices,boolean parity){}
 private void sample(NativePepperRexPlayback p,double t){int phase=t<1.4?0:t<1.9?1:t<4.4?2:0;double action=t-(phase==1?1.4:phase==2?1.9:0);p.sample(t,t>.4&&t<1.4||t>4.4,phase,action,t-.9,t>5.6?t-5.6:0);}
 @Test void groundedRigidLimbsClosedHeadHullAndTransitions(){var m=model();assertEquals(6,m.clips().size());assertEquals(6,m.quads().stream().filter(q->q.sourcePart().equals("head_outline")).count());var play=new NativePepperRexPlayback(m);var v=new Vector3f();Vector3f previous=null;for(int i=0;i<640;i++){double t=i*.01;sample(play,t);var mats=play.pose().matrices();for(var mat:mats)assertEquals(1,mat.determinant(),.0001);float low=999;for(var q:m.quads())for(var vv:q.vertices()){mats[q.bone()].transformPosition(v.set(vv[0],vv[1],vv[2]));low=Math.min(low,v.y);assertTrue(v.y>=.1199,"Rex limb/shell crosses ground");}assertEquals(.12,low,.0001);mats[3].transformPosition(v.set(0,15,-8));if(previous!=null)assertTrue(v.distance(previous)<4,"Rex transition jumped at "+t);previous=new Vector3f(v);}}
 @Test void exportProductionPoses()throws Exception{var m=model();var p=new NativeNpcPose(m);var out=new ArrayList<Frame>();for(var e:m.clips().entrySet())for(int i=0;i<=8;i++){double t=Math.round(e.getValue().length()*i/8*200)/200.;t=Math.min(t,e.getValue().length());p.reset();p.apply(e.getKey(),t);out.add(new Frame(e.getKey(),t,Arrays.stream(p.matrices()).map(x->x.get(new float[16])).toList(),true));}var play=new NativePepperRexPlayback(m);for(int i=0;i<320;i++){double t=i*.02;sample(play,t);out.add(new Frame("sequence",t,Arrays.stream(play.pose().matrices()).map(x->x.get(new float[16])).toList(),false));}var dir=Path.of("build/reports/pepper-rex-animation");Files.createDirectories(dir);Files.writeString(dir.resolve("poses.json"),new Gson().toJson(out));Files.writeString(dir.resolve("model.json"),new Gson().toJson(m));}
}
