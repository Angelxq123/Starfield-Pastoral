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
class SerpentAnimationTest {
 private NativeNpcModel model(){return new Gson().fromJson(new InputStreamReader(Objects.requireNonNull(getClass().getResourceAsStream("/serpent/model.json"))),NativeNpcModel.class);}
 record Frame(String clip,double time,List<float[]> matrices,boolean parity){}
 @Test void rigidBodyClosedShellAndSeamlessWave(){var m=model();assertEquals(6,m.quads().stream().filter(q->q.sourcePart().equals("head_outline")).count());var p=new NativeNpcPose(m);p.apply("animation.serpent.fly",0);var a=Arrays.stream(p.matrices()).map(x->x.get(new float[16])).toList();p.reset();p.apply("animation.serpent.fly",.36);for(int i=0;i<a.size();i++)assertArrayEquals(a.get(i),p.matrices()[i].get(new float[16]),.00001F);var play=new NativeSerpentPlayback(m);var v=new Vector3f();for(int i=0;i<300;i++){double t=i*.01;play.sample(t,t-1,t>2.2?t-2.2:0);for(var mat:play.pose().matrices())assertEquals(1,mat.determinant(),.0001);for(var q:m.quads())for(var vv:q.vertices()){play.pose().matrices()[q.bone()].transformPosition(v.set(vv[0],vv[1],vv[2]));assertTrue(.75+v.y*.75/16>0,"Serpent touches ground");}}}
 @Test void fullFlightWaveStaysInsideVerticalBodyWhenPitched(){
  var m=new Gson().fromJson(new InputStreamReader(Objects.requireNonNull(getClass().getResourceAsStream("/assets/stardewcraft/monster_native/serpent.json"))),NativeNpcModel.class);
  var play=new NativeSerpentPlayback(m);var v=new Vector3f();
  for(int frame=0;frame<72;frame++){
   play.sample(frame*.01,100,0);
   for(double sign:new double[]{-1,1}){
    double angle=Math.toRadians(sign*com.stardew.craft.entity.monster.MineSerpentEntity.MAX_FLIGHT_PITCH);
    for(var q:m.quads())for(var point:q.vertices()){
     play.pose().matrices()[q.bone()].transformPosition(v.set(point[0],point[1],point[2]));
     double y=.4+(v.y*.75/16-.4)*Math.cos(angle)-v.z*.75/16*Math.sin(angle);
     assertTrue(y>0&&y<.8,"Pitched shell leaves body: "+q.sourcePart()+" y="+y);
    }
   }
  }
 }
 @Test void exportProductionPoses()throws Exception{var m=model();var p=new NativeNpcPose(m);var out=new ArrayList<Frame>();for(var e:m.clips().entrySet())for(int i=0;i<=8;i++){double t=Math.round(e.getValue().length()*i/8*200)/200.;p.reset();p.apply(e.getKey(),t);out.add(new Frame(e.getKey(),t,Arrays.stream(p.matrices()).map(x->x.get(new float[16])).toList(),true));}var play=new NativeSerpentPlayback(m);for(int i=0;i<150;i++){double t=i*.02;play.sample(t,t-1,t>2.2?t-2.2:0);out.add(new Frame("sequence",t,Arrays.stream(play.pose().matrices()).map(x->x.get(new float[16])).toList(),false));}var dir=Path.of("build/reports/serpent-animation");Files.createDirectories(dir);Files.writeString(dir.resolve("poses.json"),new Gson().toJson(out));Files.writeString(dir.resolve("model.json"),new Gson().toJson(m));}
}
