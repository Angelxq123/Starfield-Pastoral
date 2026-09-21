package com.stardew.craft.monster;

import com.google.gson.Gson;
import com.stardew.craft.client.monsternative.NativeRockGolemPlayback;
import com.stardew.craft.client.npcnative.*;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import java.io.InputStreamReader;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class FarmGolemAnimationTest {
    private NativeNpcModel model(String id) {
        return new Gson().fromJson(new InputStreamReader(Objects.requireNonNull(getClass().getResourceAsStream(
                "/assets/stardewcraft/monster_native/"+id+".json"))),NativeNpcModel.class);
    }
    @Test void farmEmergenceStaysBuriedThenGroundsTheApprovedGait() {
        for(String id:List.of("wilderness_golem","iridium_golem")) {
            var m=model(id);var play=new NativeRockGolemPlayback(m,true);
            double previousTop=-100;
            for(int i=0;i<=160;i++) {
                double t=i*.01;int phase=t<.2?0:t<.9?1:2;
                play.sample(t,t>1.1,phase,Math.clamp((t-.2)/.7,0,1),100,0);
                float low=Float.POSITIVE_INFINITY,high=Float.NEGATIVE_INFINITY;var v=new Vector3f();
                for(var q:m.quads())for(var p:q.vertices()) {
                    play.pose().matrices()[q.bone()].transformPosition(v.set(p[0],p[1],p[2]));
                    assertTrue(v.isFinite());low=Math.min(low,v.y);high=Math.max(high,v.y);
                }
                if(phase==0)assertTrue(high<0,"Dormant farm golem must remain underground");
                if(phase==1 && t<.65)assertTrue(high>=previousTop-.05,"Emergence moved backwards");
                if(phase==2)assertTrue(low>=.199,"Awake hull crosses the ground");
                previousTop=high;
            }
            for(String eye:List.of("eyes_left_outline","eyes_right_outline"))
                assertEquals(6,m.quads().stream().filter(q->q.sourcePart().equals(eye)).count());
        }
    }
    record Frame(String clip,double time,List<float[]> matrices) {}
    @Test void exportAllAuthoredClipPosesForBlockbenchParity() throws Exception {
        for(String id:List.of("wilderness_golem","iridium_golem")) {
            var m=model(id);var p=new NativeNpcPose(m);var frames=new ArrayList<Frame>();
            for(var e:m.clips().entrySet())for(int i=0;i<=8;i++) {
                double t=Math.round(e.getValue().length()*i/8*200)/200.;
                p.reset();p.apply(e.getKey(),t);
                frames.add(new Frame(e.getKey(),t,Arrays.stream(p.matrices()).map(x->x.get(new float[16])).toList()));
            }
            var target=Path.of("build/reports/farm-golem-animation");Files.createDirectories(target);
            Files.writeString(target.resolve(id+"-poses.json"),new Gson().toJson(frames));
        }
    }
}
