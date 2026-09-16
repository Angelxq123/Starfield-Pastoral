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
class MummyAnimationTest {
 private NativeNpcModel model(){return new Gson().fromJson(new InputStreamReader(Objects.requireNonNull(getClass().getResourceAsStream("/mummy/model.json"))),NativeNpcModel.class);}
 record Frame(String clip,double time,List<float[]> matrices,boolean parity){}
 private void sample(NativeMummyPlayback p,double t){int phase=t<1.5?0:t<1.8?1:t<3.8?2:t<4.2?3:0;double action=t-(phase==1?1.5:phase==2?1.8:phase==3?3.8:0);p.sample(t,t>.3&&t<1.5||t>4.2,phase,action,phase==2?(int)((3.8-t)*1000):0,t-.8,t>4.8?t-4.8:0);}
 @Test void rigidLimbsAndThreeClosedHullsRemainAboveGroundAcrossEveryTransition(){var m=model();assertEquals(7,m.clips().size());for(String n:List.of("head_outline","eye_outline_left","eye_outline_right"))assertEquals(6,m.quads().stream().filter(q->q.sourcePart().equals(n)).count());var play=new NativeMummyPlayback(m);var v=new Vector3f();Vector3f prev=null;for(int i=0;i<550;i++){double t=i*.01;sample(play,t);var mats=play.pose().matrices();for(int bone=1;bone<mats.length;bone++){int parent=m.bones().get(bone).parent();if(parent>=0)assertEquals(1,new org.joml.Matrix4f(mats[parent]).invert().mul(mats[bone]).determinant(),.0001,"Individual limb scaled");}for(var q:m.quads())for(var a:q.vertices()){mats[q.bone()].transformPosition(v.set(a[0],a[1],a[2]));assertTrue(v.y>=.4999,"Eye shell/linen intersects floor");}mats[3].transformPosition(v.set(0,25,0));if(prev!=null)assertTrue(v.distance(prev)<3,"Mummy transition jumped");prev=new Vector3f(v);}}
 @Test void modelFitsStandingAndCollapsedAabbs(){var m=model();var play=new NativeMummyPlayback(m);var v=new Vector3f();for(int i=0;i<480;i++){double t=i*.01;sample(play,t);int phase=t<1.5?0:t<1.8?1:t<3.8?2:t<4.2?3:0;for(int yaw=0;yaw<360;yaw+=30){var bounds=MummyLifecycle.collisionBox(0,0,0,yaw,1,phase);var rot=new org.joml.Matrix4f().rotateY((float)Math.toRadians(180-yaw));var mats=play.pose().matrices();if(play.showBody())for(var q:m.quads())for(var a:q.vertices()){mats[q.bone()].transformPosition(v.set(a[0],a[1],a[2]));rot.transformPosition(v);assertTrue(bounds.contains(v.x/16,v.y/16,v.z/16),"Mummy hull outside AABB at "+t+" / "+phase);}}}}
 @Test void exportProductionPoses()throws Exception{var m=model();var p=new NativeNpcPose(m);var out=new ArrayList<Frame>();for(var e:m.clips().entrySet())for(int i=0;i<=8;i++){double t=Math.round(e.getValue().length()*i/8*200)/200.;p.reset();p.apply(e.getKey(),t);out.add(new Frame(e.getKey(),t,Arrays.stream(p.matrices()).map(x->x.get(new float[16])).toList(),true));}var play=new NativeMummyPlayback(m);for(int i=0;i<280;i++){double t=i*.02;sample(play,t);out.add(new Frame("sequence",t,Arrays.stream(play.pose().matrices()).map(x->x.get(new float[16])).toList(),false));}var dir=Path.of("build/reports/mummy-animation");Files.createDirectories(dir);Files.writeString(dir.resolve("poses.json"),new Gson().toJson(out));Files.writeString(dir.resolve("model.json"),new Gson().toJson(m));}
}
