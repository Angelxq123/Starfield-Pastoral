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
class DustSpiritAnimationTest {
    private NativeNpcModel model(){return new Gson().fromJson(new InputStreamReader(Objects.requireNonNull(getClass().getResourceAsStream("/dust_spirit/model.json"))),NativeNpcModel.class);}
    @Test void allFramesRemainFiniteAndHullClearOfGround(){
        var m=model();assertEquals(20,m.quads().size());var p=new NativeDustSpiritPlayback(m);var v=new Vector3f();
        for(int i=0;i<=256;i++){p.sample(i*.02,-i%50,.75,i*.02-2,0);var matrices=p.pose().matrices();
            for(var q:m.quads())for(var xyz:q.vertices()){matrices[q.bone()].transformPosition(v.set(xyz[0],xyz[1],xyz[2]));assertTrue(Float.isFinite(v.x)&&Float.isFinite(v.y)&&Float.isFinite(v.z));if(q.sourcePart().endsWith("_outline"))assertTrue(v.y>.2,"Hull touched ground");}
        }
    }
    record Frame(String clip,double time,List<float[]> matrices,boolean parity){}
    @Test void exportProductionPoses()throws Exception{
        var m=model();var p=new NativeNpcPose(m);var out=new ArrayList<Frame>();
        for(var e:m.clips().entrySet())for(int i=0;i<=8;i++){double t=e.getValue().length()*i/8;p.reset();p.apply(e.getKey(),t);out.add(new Frame(e.getKey(),t,Arrays.stream(p.matrices()).map(x->x.get(new float[16])).toList(),true));}
        var playback=new NativeDustSpiritPlayback(m);var physics=new DustSpiritMotion();var r=net.minecraft.util.RandomSource.create(55);int step=0;float deathHeight=0;
        for(int i=0;i<180;i++){
            double t=i*.02;while(step<=t*60){physics.jumpPhysics();if(physics.offset()==0)physics.launch(r);step++;}
            double death=t>2.95?t-2.95:0;playback.sample(t,physics.offset(),1,t-2.2,death);
            var matrices=Arrays.stream(playback.pose().matrices()).map(org.joml.Matrix4f::new).toList();
            // Preview the entity's world height once, after the exact production pose.
            if(death==0)deathHeight=-physics.offset()/4F;float height=death>0?deathHeight:-physics.offset()/4F;for(var mat:matrices)mat.m31(mat.m31()+height);
            out.add(new Frame("sequence",t,matrices.stream().map(x->x.get(new float[16])).toList(),false));
        }
        var target=Path.of("build/reports/dust-spirit-animation");Files.createDirectories(target);Files.writeString(target.resolve("model.json"),new Gson().toJson(m));Files.writeString(target.resolve("poses.json"),new Gson().toJson(out));
    }
}
