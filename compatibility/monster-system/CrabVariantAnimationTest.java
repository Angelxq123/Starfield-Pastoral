package com.stardew.craft.monster;
import com.google.gson.*;
import com.stardew.craft.client.monsternative.*;
import com.stardew.craft.client.npcnative.*;
import org.junit.jupiter.api.Test;
import org.joml.Vector3f;
import java.io.InputStreamReader;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import static org.junit.jupiter.api.Assertions.*;

class CrabVariantAnimationTest {
    private NativeNpcModel model(String id){return new Gson().fromJson(new InputStreamReader(Objects.requireNonNull(getClass().getResourceAsStream("/"+id+"/model.json"))),NativeNpcModel.class);}
    private String point(float x,float y,float z){return Math.round(x*1000)+","+Math.round(y*1000)+","+Math.round(z*1000);}
    @Test void disguiseIsExactlyTheShippedNodeAndItsTexture()throws Exception{
        for(String id:List.of("lava_crab","iridium_crab")){
            int node=id.equals("lava_crab")?56:765;var m=model(id);var pose=new NativeNpcPose(m);NativeRockCrabMotion.sample(m,pose,"disguise",0);var matrices=pose.matrices();
            var actual=new HashSet<String>();var p=new Vector3f();
            for(var q:m.quads())if(NativeRockCrabMotion.visible(q.sourcePart(),"disguise",0))for(var v:q.vertices()){matrices[q.bone()].transformPosition(p.set(v[0],v[1],v[2]));actual.add(point(p.x,p.y,p.z));}
            var source=JsonParser.parseReader(new InputStreamReader(Objects.requireNonNull(getClass().getResourceAsStream("/assets/stardewcraft/models/block/mine/nodes/stone_"+node+".json")))).getAsJsonObject();var expected=new HashSet<String>();
            for(var e:source.getAsJsonArray("elements")){var cube=e.getAsJsonObject();var from=cube.getAsJsonArray("from");var to=cube.getAsJsonArray("to");
                for(int i=0;i<8;i++){p.set(((i&1)==0?from:to).get(0).getAsFloat(),((i&2)==0?from:to).get(1).getAsFloat(),((i&4)==0?from:to).get(2).getAsFloat());
                    if(cube.has("rotation")){var r=cube.getAsJsonObject("rotation");var o=r.getAsJsonArray("origin");var origin=new Vector3f(o.get(0).getAsFloat(),o.get(1).getAsFloat(),o.get(2).getAsFloat());p.sub(origin);float a=(float)Math.toRadians(r.get("angle").getAsDouble());switch(r.get("axis").getAsString()){case "x"->p.rotateX(a);case "y"->p.rotateY(a);case "z"->p.rotateZ(a);}p.add(origin);}
                    expected.add(point(p.x-8,p.y,p.z-8));
                }
            }
            assertEquals(expected,actual,id+" changed its camouflage silhouette");
            var atlas=ImageIO.read(Objects.requireNonNull(getClass().getResourceAsStream("/assets/stardewcraft/textures/entity/monster_native/"+id+".png")));var stone=ImageIO.read(Objects.requireNonNull(getClass().getResourceAsStream("/assets/stardewcraft/textures/block/mine/nodes/stone_"+node+".png")));
            for(int y=0;y<64;y++)for(int x=0;x<64;x++)assertEquals(stone.getRGB(x,y),atlas.getRGB(x+128,y));
        }
    }
    @Test void allClipsPreserveClosedHullsAndFootClearance(){
        for(String id:List.of("lava_crab","iridium_crab")){var m=model(id);assertEquals(15,m.clips().size());var pose=new NativeNpcPose(m);var v=new Vector3f();
            var parts=m.quads().stream().map(NativeNpcModel.Quad::sourcePart).distinct().filter(n->n.contains("outline")||n.contains("轮廓")).toList();
            for(String part:parts)assertEquals(6,m.quads().stream().filter(q->q.sourcePart().equals(part)).count(),part);
            for(var e:m.clips().entrySet()){String clip=e.getKey().substring("animation.rock_crab.".length());for(int i=0;i<=180;i++){double t=e.getValue().length()*i/180;NativeRockCrabMotion.sample(m,pose,clip,t);var matrices=pose.matrices();
                for(var q:m.quads())if(NativeRockCrabMotion.visible(q.sourcePart(),clip,t))for(var p:q.vertices()){matrices[q.bone()].transformPosition(v.set(p[0],p[1],p[2]));
                    if(parts.contains(q.sourcePart()))assertTrue(v.y>=.199,id+" / "+clip+" / "+q.sourcePart()+" hull touches floor: "+v.y);
                }
            }}
        }
    }
    record Frame(String clip,double time,List<float[]> matrices,List<String> hidden,boolean parity){}
    @Test void exportActualClipsAndInterruptedPlayback()throws Exception{
        for(String id:List.of("lava_crab","iridium_crab")){var m=model(id);var p=new NativeNpcPose(m);var out=new ArrayList<Frame>();
            for(var e:m.clips().entrySet())for(int i=0;i<=6;i++){double t=Math.round(e.getValue().length()*i/6*200)/200.;p.reset();p.apply(e.getKey(),t);out.add(new Frame(e.getKey(),t,Arrays.stream(p.matrices()).map(x->x.get(new float[16])).toList(),List.of(),true));}
            var play=new NativeRockCrabPlayback(m);
            for(int i=0;i<320;i++){double t=i*.02;int phase=t<.6?0:t<3.3?1:2;boolean moving=t>=1.2&&t<2.5||t>=3.8;play.sample(phase,moving,0,t,t-2.6,t>=5.7?t-5.7:0);var hidden=m.quads().stream().map(NativeNpcModel.Quad::sourcePart).distinct().filter(n->!play.visible(n)).toList();out.add(new Frame("sequence",t,Arrays.stream(play.pose().matrices()).map(x->x.get(new float[16])).toList(),hidden,false));}
            var dir=Path.of("build/reports/"+id+"-animation");Files.createDirectories(dir);Files.writeString(dir.resolve("model.json"),new Gson().toJson(m));Files.writeString(dir.resolve("poses.json"),new Gson().toJson(out));
        }
    }
}
