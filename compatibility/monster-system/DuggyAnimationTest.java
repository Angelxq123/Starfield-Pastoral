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

class DuggyAnimationTest {
    private NativeNpcModel model(){return new Gson().fromJson(new InputStreamReader(Objects.requireNonNull(getClass().getResourceAsStream("/duggy/model.json"))),NativeNpcModel.class);}
    @Test void emergingBodyIsClippedWithoutStretchingItsTexture(){
        var vertices=List.of(new NativeGroundClip.Vertex(0,-1,0,0,1),new NativeGroundClip.Vertex(1,-1,0,1,1),new NativeGroundClip.Vertex(1,1,0,1,0),new NativeGroundClip.Vertex(0,1,0,0,0));
        var clipped=NativeGroundClip.clip(vertices,0);assertEquals(4,clipped.size());
        for(var v:clipped){assertTrue(v.y()>=0);if(v.y()==0)assertEquals(.5F,v.v());}
        var m=model();assertEquals(6,m.clips().size());assertTrue(m.quads().stream().anyMatch(q -> q.sourcePart().equals("feeler_curl")), "Curled head feeler missing");var play=new NativeDuggyPlayback(m);var vector=new Vector3f();
        for(int i=0;i<500;i++){
            play.sample(i*.005,i/50.,1,0);var matrices=play.pose().matrices();
            for(var q:m.quads()){
                var face=new ArrayList<NativeGroundClip.Vertex>();for(var v:q.vertices()){matrices[q.bone()].transformPosition(vector.set(v[0],v[1],v[2]));face.add(new NativeGroundClip.Vertex(vector.x,vector.y,vector.z,v[3],v[4]));}
                for(var v:NativeGroundClip.clip(face,.05F))assertTrue(v.y()>=.04999F);
            }
        }
    }
    @Test void sourceStageBoundariesAndHitEntryAreContinuous(){
        var m=model();
        for(double boundary:new double[]{2,4,8}){
            var a=new NativeDuggyPlayback(m);var b=new NativeDuggyPlayback(m);
            a.sample(1,boundary-.00001,1,0);b.sample(1,boundary,0,0);
            for(int i=0;i<m.bones().size();i++)assertTrue(a.pose().matrices()[i].equals(b.pose().matrices()[i],.0001F),"Boundary "+boundary);
        }
    }
    record Frame(String clip,double time,List<float[]> matrices,boolean parity){}
    @Test void exportProductionPoses() throws Exception {
        var m=model();var p=new NativeNpcPose(m);var out=new ArrayList<Frame>();
        for(var e:m.clips().entrySet())for(int i=0;i<=8;i++){
            double t=e.getValue().length()*i/8;p.reset();p.apply(e.getKey(),t);out.add(new Frame(e.getKey(),t,Arrays.stream(p.matrices()).map(x->x.get(new float[16])).toList(),true));
        }
        var play=new NativeDuggyPlayback(m);
        for(int i=0;i<180;i++){
            double t=i*.02,cycle=t%1.72,cursor=cycle<.4?cycle/.1:4+(cycle-.4)/.22;
            play.sample(t,cursor,t-2.6,t>2.95?t-2.95:0);
            out.add(new Frame("sequence",t,Arrays.stream(play.pose().matrices()).map(x->x.get(new float[16])).toList(),false));
        }
        var target=Path.of("build/reports/duggy-animation");Files.createDirectories(target);Files.writeString(target.resolve("model.json"),new Gson().toJson(m));Files.writeString(target.resolve("poses.json"),new Gson().toJson(out));
    }
}
