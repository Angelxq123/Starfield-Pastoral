package com.stardew.craft.monster;
import com.google.gson.Gson;
import com.stardew.craft.client.monsternative.*;
import com.stardew.craft.client.npcnative.*;
import org.junit.jupiter.api.Test;
import java.io.InputStreamReader;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class GhostAnimationTest {
    private NativeNpcModel model(String id){return new Gson().fromJson(new InputStreamReader(Objects.requireNonNull(getClass().getResourceAsStream("/"+id+"/model.json"))),NativeNpcModel.class);}
    @Test void bothVariantsShareClipsAndNoInternalNeckFaces(){
        var a=model("ghost");var b=model("carbon_ghost");assertEquals(a.clips().keySet(),b.clips().keySet());assertEquals(44,a.quads().size());
        for(var q:a.quads())assertTrue(q.translucent());
        assertEquals(5,a.quads().stream().filter(q->q.sourcePart().equals("head")).count());assertEquals(5,a.quads().stream().filter(q->q.sourcePart().equals("torso")).count());
        var play=new NativeGhostPlayback(a);for(int i=0;i<300;i++){play.sample(i*.01,false,i*.01-1,i>250?(i-250)*.01:0);for(var matrix:play.pose().matrices()){assertTrue(matrix.isFinite());assertTrue(Math.abs(matrix.determinant())>.1);}}
    }
    @Test void steeringNeverIntroducesAHitTurnLockAndRestoresInertia(){
        var a=new GhostSteering();var r=net.minecraft.util.RandomSource.create(12);for(int i=0;i<400;i++){a.decay();a.animate(640,320,r);assertTrue(Double.isFinite(a.x())&&Double.isFinite(a.y()));}
        var north=new GhostSteering();north.animate(0,-640,net.minecraft.util.RandomSource.create(3));assertTrue(north.y()>0,"Source north trajectory has positive Y velocity");a.knockback(-30,40);assertEquals(-30,a.x());assertEquals(40,a.y());var b=new GhostSteering();b.load(a.save());assertEquals(a.save(),b.save());a.decay();assertEquals(-26.25,a.x());
    }
    record Frame(String clip,double time,List<float[]> matrices,boolean parity){}
    @Test void exportProductionPoses()throws Exception{
        for(String id:List.of("ghost","carbon_ghost")){
            var m=model(id);var p=new NativeNpcPose(m);var out=new ArrayList<Frame>();
            for(var e:m.clips().entrySet())for(int i=0;i<=8;i++){double t=e.getValue().length()*i/8;p.reset();p.apply(e.getKey(),t);out.add(new Frame(e.getKey(),t,Arrays.stream(p.matrices()).map(x->x.get(new float[16])).toList(),true));}
            var play=new NativeGhostPlayback(m);float deathHeight=0;
            for(int i=0;i<150;i++){double t=i*.02,death=t>2.5?t-2.5:0;play.sample(t,t>1.8,t-1.8,death);var mats=Arrays.stream(play.pose().matrices()).map(org.joml.Matrix4f::new).toList();if(death==0)deathHeight=(float)(7.2-Math.sin(t*Math.PI*2)*5);for(var mat:mats)mat.m31(mat.m31()+deathHeight);out.add(new Frame("sequence",t,mats.stream().map(x->x.get(new float[16])).toList(),false));}
            var target=Path.of("build/reports/"+id+"-animation");Files.createDirectories(target);Files.writeString(target.resolve("model.json"),new Gson().toJson(m));Files.writeString(target.resolve("poses.json"),new Gson().toJson(out));
        }
    }
}
