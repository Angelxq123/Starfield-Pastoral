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
class ArmoredBugAnimationTest {
    private NativeNpcModel model(){return new Gson().fromJson(new InputStreamReader(Objects.requireNonNull(getClass().getResourceAsStream("/armored_bug/model.json"))),NativeNpcModel.class);}
    @Test void independentMembranesAndClosedArmorSurviveReactions(){var m=model();assertEquals(4,m.clips().size());assertEquals(4,m.quads().stream().filter(NativeNpcModel.Quad::translucent).count());
        for(String part:List.of("abdomen_outline","elytron_outline_left","elytron_outline_right"))assertEquals(6,m.quads().stream().filter(q->q.sourcePart().equals(part)).count());
        var play=new NativeBugPlayback(m);var v=new Vector3f();
        for(int i=0;i<240;i++){double t=i*.01;play.sample(t,t-.6,t>1.5?t-1.5:0,2);var matrices=play.pose().matrices();for(var q:m.quads())for(var p:q.vertices()){matrices[q.bone()].transformPosition(v.set(p[0],p[1],p[2]));assertTrue(v.y>=.249,"Negative armor intersects ground");}for(var x:matrices)assertTrue(x.isFinite()&&Math.abs(x.determinant())>.1);}
    }
    record Frame(String clip,double time,List<float[]> matrices,double swayX,double swayZ,boolean parity){}
    @Test void exportProductionPoses()throws Exception{var m=model();var p=new NativeNpcPose(m);var frames=new ArrayList<Frame>();
        for(var e:m.clips().entrySet())for(int i=0;i<=8;i++){double t=Math.round(e.getValue().length()*i/8*200)/200.;p.reset();p.apply(e.getKey(),t);frames.add(new Frame(e.getKey(),t,Arrays.stream(p.matrices()).map(x->x.get(new float[16])).toList(),0,0,true));}
        var play=new NativeBugPlayback(m);for(int i=0;i<180;i++){double t=i*.02;play.sample(t,t-1.2,t>2.5?t-2.5:0,2);frames.add(new Frame("sequence",t,Arrays.stream(play.pose().matrices()).map(x->x.get(new float[16])).toList(),play.swayX(),play.swayZ(),false));}
        var dir=Path.of("build/reports/armored-bug-animation");Files.createDirectories(dir);Files.writeString(dir.resolve("model.json"),new Gson().toJson(m));Files.writeString(dir.resolve("poses.json"),new Gson().toJson(frames));
    }
}
