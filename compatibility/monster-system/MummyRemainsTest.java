package com.stardew.craft.monster;
import com.google.gson.Gson;
import com.stardew.craft.client.monsternative.NativeMummyPlayback;
import com.stardew.craft.client.npcnative.NativeNpcModel;
import java.io.InputStreamReader;
import java.nio.file.*;
import java.util.*;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class MummyRemainsTest {
 NativeNpcModel model(String name){return new Gson().fromJson(new InputStreamReader(Objects.requireNonNull(getClass().getResourceAsStream("/"+name+"/model.json"))),NativeNpcModel.class);}
 record Frame(double time,int phase,List<float[]> body,List<float[]> remains,boolean showBody,boolean showRemains){}
 @Test void staticHeapAndSourceReverseTransition()throws Exception{
  var body=model("mummy");var heap=model("mummy_remains");var play=new NativeMummyPlayback(body,heap);var frames=new ArrayList<Frame>();List<float[]> staticHeap=null;
  assertTrue(heap.quads().stream().noneMatch(q->q.sourcePart().contains("eye")||q.sourcePart().contains("head")));
  for(int i=0;i<=710;i++){
   double t=i*.02;int phase=t<1.5?0:t<1.8?1:t<11.8?2:t<12.2?3:0;
   double action=t-(phase==1?1.5:phase==2?1.8:phase==3?11.8:0);int remaining=phase==2?(int)Math.round((11.8-t)*1000):0;
   play.sample(t,t>.3&&t<1.5||t>12.2,phase,action,remaining,-1,t>13.5?t-13.5:0);
   assertTrue(play.showBody()||play.showRemains(),"Both forms disappeared at "+t);
   if(phase==2){
    assertFalse(play.showBody());assertTrue(play.showRemains());var matrices=play.remainsPose().matrices();var point=new Vector3f();
    for(var q:heap.quads())for(var v:q.vertices()){
     matrices[q.bone()].transformPosition(point.set(v[0],v[1],v[2]));
     assertTrue(point.y>=.4999&&point.y<7.36,"Heap must stay low and above the floor");
     for(int yaw=0;yaw<360;yaw+=45){var rotated=new org.joml.Matrix4f().rotateY((float)Math.toRadians(180-yaw)).transformPosition(new Vector3f(point));assertTrue(MummyLifecycle.collisionBox(0,0,0,yaw,1,phase).contains(rotated.x/16,rotated.y/16,rotated.z/16));}
    }
    var values=Arrays.stream(matrices).map(x->x.get(new float[16])).toList();
    if(remaining>=2000){if(staticHeap!=null)for(int b=0;b<values.size();b++)assertArrayEquals(staticHeap.get(b),values.get(b),.00001F,"Waiting heap must not breathe");staticHeap=values;}
   }
   frames.add(new Frame(t,phase,Arrays.stream(play.pose().matrices()).map(x->x.get(new float[16])).toList(),Arrays.stream(play.remainsPose().matrices()).map(x->x.get(new float[16])).toList(),play.showBody(),play.showRemains()));
  }
  var out=Path.of("build/reports/mummy-remains");Files.createDirectories(out);Files.writeString(out.resolve("sequence.json"),new Gson().toJson(frames));
 }
}
