package com.stardew.craft.monster;
import com.google.gson.Gson;
import com.stardew.craft.client.monsternative.*;
import com.stardew.craft.client.npcnative.*;
import org.junit.jupiter.api.Test;
import org.joml.*;
import java.io.InputStreamReader;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class BigSlimeAnimationTest {
    private NativeNpcModel model(){return new Gson().fromJson(new InputStreamReader(Objects.requireNonNull(getClass().getResourceAsStream("/big_slime/model.json"))),NativeNpcModel.class);}
    @Test void closedHullStaysClearAndHeldItemDoesNotSquash(){var m=model();assertEquals(4,m.clips().size());assertEquals(6,m.quads().stream().filter(NativeNpcModel.Quad::translucent).count());assertEquals(6,m.quads().stream().filter(q->q.sourcePart().equals("gel_outline")).count());var play=new NativeBigSlimePlayback(m);var v=new Vector3f();Vector3f previous=null;int socket=2;
        for(int i=0;i<640;i++){double t=i*.005;play.sample(t,t>.4&&t<1.5,t-.8,t>2.4?t-2.4:0);var matrices=play.pose().matrices();float lift=NativeMonsterPresentation.groundLift(m,matrices,new Matrix4f(),1/16F);assertEquals(1,matrices[socket].determinant(),.00001,"Held item scaled with gel");for(var q:m.quads())for(var p:q.vertices()){matrices[q.bone()].transformPosition(v.set(p[0],p[1],p[2]));assertTrue(v.y/16+lift>=.03124,"Hull intersects floor");}for(var x:matrices)assertTrue(x.isFinite()&&java.lang.Math.abs(x.determinant())>.1);matrices[1].transformPosition(v.set(12,18,10));if(previous!=null)assertTrue(v.distance(previous)<1,"Pose jumped between states");previous=new Vector3f(v);}
    }
    @Test void livingGelFitsItsWholeAabbAtEveryYawAndRimIsBrighter(){var m=model();var play=new NativeBigSlimePlayback(m);var v=new Vector3f();for(int i=0;i<240;i++){double t=i*.01;play.sample(t,t>.4,t-.8,0);var mats=play.pose().matrices();float lift=NativeMonsterPresentation.groundLift(m,mats,new Matrix4f(),1/16F);for(int yaw=0;yaw<360;yaw+=15){var box=BigSlimeRules.collisionBox(0,0,0,yaw,1);var rot=new Matrix4f().rotateY((float)java.lang.Math.toRadians(180-yaw));for(var q:m.quads())for(var p:q.vertices()){mats[q.bone()].transformPosition(v.set(p[0],p[1],p[2]));rot.transformPosition(v);assertTrue(box.contains(v.x/16,v.y/16+lift,v.z/16),"Live gel exceeds collision envelope");}}}for(int color:new int[]{0x00ff00,0x40e0d0,0xff0000,0x8a2be2}){int body=NativeMonsterPresentation.slimeTint(color,false),rim=NativeMonsterPresentation.bigSlimeOutlineTint(color);double b=((body>>16&255)*.2126+(body>>8&255)*.7152+(body&255)*.0722)/255,r=((rim>>16&255)*.2126+(rim>>8&255)*.7152+(rim&255)*.0722)/255;assertTrue(r>b+.05,"Rim lost perceptual contrast");}}
    record Frame(String clip,double time,List<float[]> matrices,float lift,boolean parity){}
    @Test void exportProductionPoses()throws Exception{var m=model();var p=new NativeNpcPose(m);var frames=new ArrayList<Frame>();for(var e:m.clips().entrySet())for(int i=0;i<=10;i++){double t=java.lang.Math.round(e.getValue().length()*i/10*200)/200.;p.reset();p.apply(e.getKey(),t);frames.add(new Frame(e.getKey(),t,Arrays.stream(p.matrices()).map(x->x.get(new float[16])).toList(),0,true));}
        var play=new NativeBigSlimePlayback(m);for(int i=0;i<225;i++){double t=i*.02;play.sample(t,t>.8&&t<2.6,t-1.8,t>3.6?t-3.6:0);frames.add(new Frame("sequence",t,Arrays.stream(play.pose().matrices()).map(x->x.get(new float[16])).toList(),NativeMonsterPresentation.groundLift(m,play.pose().matrices(),new Matrix4f(),1/16F),false));}
        var dir=Path.of("build/reports/big-slime-animation");Files.createDirectories(dir);Files.writeString(dir.resolve("model.json"),new Gson().toJson(m));Files.writeString(dir.resolve("poses.json"),new Gson().toJson(frames));
    }
}
