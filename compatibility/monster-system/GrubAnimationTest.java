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

class GrubAnimationTest {
    private NativeNpcModel model() { return new Gson().fromJson(new InputStreamReader(Objects.requireNonNull(getClass().getResourceAsStream("/grub/model.json"))),NativeNpcModel.class); }
    private void aboveFloor(NativeNpcModel m,NativeNpcPose pose) {
        var matrices=pose.matrices();var v=new Vector3f();
        for(var q:m.quads())for(var p:q.vertices())assertTrue(matrices[q.bone()].transformPosition(v.set(p[0],p[1],p[2])).y>=.399F);
    }
    @Test void cocoonChangesStructureAndAllClipFramesStayAboveGround() {
        var m=model();assertEquals(6,m.clips().size());assertEquals(60,m.quads().size());
        var p=new NativeNpcPose(m);var rest=new NativeNpcPose(m);
        for(var e:m.clips().entrySet()) {
            var clip=e.getValue();
            if(clip.loop())for(var t:clip.tracks())assertArrayEquals(t.keys().getFirst().after(),t.keys().getLast().before());
            if(e.getKey().endsWith("hit"))continue;
            for(int i=0;i<=320;i++) {
                NativeGrubMotion.sample(m,p,e.getKey().substring("animation.grub.".length()),clip.length()*i/320);
                aboveFloor(m,p);
            }
        }
        NativeGrubMotion.sample(m,p,"pupa",0);
        int head=java.util.stream.IntStream.range(0,m.bones().size()).filter(i->m.bones().get(i).name().equals("head")).findFirst().orElseThrow();
        assertFalse(p.matrices()[head].equals(rest.matrices()[head],.1F),"Pupa only changed texture");
    }
    @Test void arbitraryStopsPupationAndDeathDoNotSnapOrSink() {
        var m=model();
        for(double t:new double[]{.1,.23,.45,.67}) {
            var player=new NativeGrubPlayback(m);player.sample(t,0,true,t,0,2,0);
            var before=Arrays.stream(player.pose().matrices()).map(x->x.get(new float[16])).toList();
            player.sample(t+.001,0,false,t+.001,0,2,0);
            for(int b=0;b<before.size();b++)assertArrayEquals(before.get(b),player.pose().matrices()[b].get(new float[16]),1e-5F);
            for(int i=1;i<=20;i++){player.sample(t+.001+i*.01,0,false,t+.001+i*.01,0,2,0);aboveFloor(m,player.pose());}
            player.sample(t+.22,2,false,0,0,2,0);
            for(int i=1;i<=40;i++){player.sample(t+.22+i*.01,2,false,i*.01,i*.01/.4,2,0);aboveFloor(m,player.pose());}
            player.sample(t+.63,3,false,0,1,2,0);
            player.sample(t+.64,3,false,.01,1,2,.001);
            for(int i=1;i<=60;i++){player.sample(t+.64+i*.01,3,false,.01+i*.01,1,2,.001+i*.01);aboveFloor(m,player.pose());}
        }
    }
    record Frame(String clip,double time,List<float[]> matrices,boolean parity) {}
    @Test void exportProductionPosesForBlockbenchComparison() throws Exception {
        var m=model();var p=new NativeNpcPose(m);var frames=new ArrayList<Frame>();
        for(var e:m.clips().entrySet())for(int i=0;i<=8;i++) {
            double t=e.getValue().length()*i/8;p.reset();p.apply(e.getKey(),t);
            frames.add(new Frame(e.getKey(),t,Arrays.stream(p.matrices()).map(x->x.get(new float[16])).toList(),true));
        }
        var playback=new NativeGrubPlayback(m);
        for(int i=0;i<300;i++) {
            double t=i*.02;int phase=t<2?0:t<2.4?2:3;
            playback.sample(t,phase,t<1.65,phase==2?t-2:phase==3?t-2.4:t,Math.max(0,(t-2)/.4),t-.9,t>=5.25?t-5.25:0);
            frames.add(new Frame("sequence",t,Arrays.stream(playback.pose().matrices()).map(x->x.get(new float[16])).toList(),false));
        }
        var out=Path.of("build/reports/grub-animation");Files.createDirectories(out);
        Files.writeString(out.resolve("poses.json"),new Gson().toJson(frames));Files.writeString(out.resolve("model.json"),new Gson().toJson(m));
    }
}
