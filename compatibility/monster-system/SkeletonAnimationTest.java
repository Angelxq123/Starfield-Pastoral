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
class SkeletonAnimationTest {
    private NativeNpcModel model(){return new Gson().fromJson(new InputStreamReader(Objects.requireNonNull(getClass().getResourceAsStream("/skeleton/model.json"))),NativeNpcModel.class);}
    @Test void sourceClockCarriesWalkTimeAndInterruptDoesNotShoot(){
        var fresh=new SkeletonThrowClock();fresh.begin();int frames=0;while(!fresh.step())assertTrue(++frames<50);frames++;
        var carried=new SkeletonThrowClock();for(int i=0;i<8;i++)carried.walk();carried.begin();int accelerated=0;while(!carried.step())assertTrue(++accelerated<50);accelerated++;
        assertTrue(accelerated<frames,"Throw lost the AnimatedSprite carried timer");assertTrue(frames>=36&&frames<=40);
        carried.begin();for(int i=0;i<5;i++)carried.step();var copy=new SkeletonThrowClock();copy.load(carried.save());assertEquals(carried.save(),copy.save());copy.interrupt();for(int i=0;i<60;i++)assertFalse(copy.step());
    }
    @Test void rigidFeetStayGroundedAndTransitionDoesNotSnap(){
        var m=model();var play=new NativeSkeletonPlayback(m);float[] previous=null;double largest=0,largestAt=0;
        for(int i=0;i<400;i++){double t=i*.01;int action=t>=1.5&&t<2.1?1:0;double progress=(t-1.5)/.6,death=t>3.2?t-3.2:0;play.sample(t,t>.2&&t<1.2,action,progress,t-2.1,t-2.6,death);
            var matrices=play.pose().matrices();float lowest=Float.POSITIVE_INFINITY;var v=new Vector3f();
            for(var q:m.quads())for(var p:q.vertices())lowest=Math.min(lowest,matrices[q.bone()].transformPosition(v.set(p[0],p[1],p[2])).y);
            assertTrue(lowest>=-.0001,"Geometry crosses floor at "+t+": "+lowest);
            var hand=play.pose().boneMatrix("throw_socket").transformPosition(new Vector3f(5,8.5F,0));float[] current={hand.x,hand.y,hand.z};
            if(previous!=null){double distance=0;for(int k=0;k<3;k++)distance+=Math.pow(current[k]-previous[k],2);if(Math.sqrt(distance)>largest){largest=Math.sqrt(distance);largestAt=t;}}previous=current;
            for(var matrix:matrices)assertTrue(matrix.isFinite()&&Math.abs(matrix.determinant())>.1);
        }
        assertTrue(largest<3,"Hand snapped "+largest+" model units in 10ms at "+largestAt);
    }
    @Test void releaseReachesForwardAndEyesHaveClosedShells(){
        var m=model();var pose=new NativeNpcPose(m);pose.apply("animation.skeleton.throw",.6);
        var hand=pose.boneMatrix("throw_socket").transformPosition(new Vector3f(5,8.5F,0));
        assertTrue(hand.z < -6 && hand.y > 12, "Throw must release in front of the chest, not behind the back: "+hand);
        for(String side:List.of("left","right"))assertEquals(6,m.quads().stream().filter(q->q.sourcePart().equals("eyes_"+side+"_outline")).count());
    }
    record Frame(String clip,double time,List<float[]> matrices,boolean parity){}
    @Test void exportProductionPoses()throws Exception{
        var m=model();var p=new NativeNpcPose(m);var out=new ArrayList<Frame>();
        for(var e:m.clips().entrySet())for(int i=0;i<=8;i++){double t=e.getValue().length()*i/8;p.reset();p.apply(e.getKey(),t);out.add(new Frame(e.getKey(),t,Arrays.stream(p.matrices()).map(x->x.get(new float[16])).toList(),true));}
        var play=new NativeSkeletonPlayback(m);
        for(int i=0;i<220;i++){double t=i*.02;int action=t>=1.6&&t<2.2?1:0;play.sample(t,t>.2&&t<1.3,action,(t-1.6)/.6,t-2.2,t-2.8,t>3.6?t-3.6:0);out.add(new Frame("sequence",t,Arrays.stream(play.pose().matrices()).map(x->x.get(new float[16])).toList(),false));}
        var target=Path.of("build/reports/skeleton-animation");Files.createDirectories(target);Files.writeString(target.resolve("model.json"),new Gson().toJson(m));Files.writeString(target.resolve("poses.json"),new Gson().toJson(out));
    }
}
