package com.stardew.craft.client.npcnative;

import com.google.gson.Gson;
import com.stardew.craft.npc.animation.SamActivity;
import com.stardew.craft.npc.runtime.SamSchedulePolicy;
import net.minecraft.nbt.CompoundTag;
import org.joml.Matrix4f;
import java.nio.file.*;

/** Calendar boundaries and real native pose transitions, without launching a game client. */
public final class SamScheduleChecks {
    static void require(boolean ok,String label) { if(!ok) throw new AssertionError(label); }
    static double distance(Matrix4f[] a,Matrix4f[] b) {
        double max=0;
        for(int i=0;i<a.length;i++) {
            var x=a[i].get(new float[16]);var y=b[i].get(new float[16]);
            for(int j=0;j<16;j++) { require(Float.isFinite(y[j]),"finite pose");max=Math.max(max,Math.abs(x[j]-y[j])); }
        }
        return max;
    }
    static Matrix4f[] snapshot(NativeNpcPose p) { return java.util.Arrays.stream(p.matrices()).map(Matrix4f::new).toArray(Matrix4f[]::new); }
    record Box(String name,int bone,org.joml.Vector3f min,org.joml.Vector3f max) {}
    static java.util.List<Box> boxes(NativeNpcModel model) {
        var result=new java.util.LinkedHashMap<String,Box>();
        for(var q:model.quads()) {
            var box=result.computeIfAbsent(q.sourcePart(),n->new Box(n,q.bone(),
                    new org.joml.Vector3f(Float.POSITIVE_INFINITY),new org.joml.Vector3f(Float.NEGATIVE_INFINITY)));
            for(var p:q.vertices()) {var v=new org.joml.Vector3f(p[0],p[1],p[2]);box.min.min(v);box.max.max(v);}
        }
        return new java.util.ArrayList<>(result.values());
    }
    static double gap(Box a,Box b,Matrix4f[] m) {
        var ac=m[a.bone].transformPosition(new org.joml.Vector3f(a.min).add(a.max).mul(.5f));
        var bc=m[b.bone].transformPosition(new org.joml.Vector3f(b.min).add(b.max).mul(.5f));
        var ah=new org.joml.Vector3f(a.max).sub(a.min).mul(.5f);var bh=new org.joml.Vector3f(b.max).sub(b.min).mul(.5f);
        var aa=new org.joml.Vector3f[3];var ba=new org.joml.Vector3f[3];var axes=new java.util.ArrayList<org.joml.Vector3f>();
        for(int i=0;i<3;i++) {aa[i]=m[a.bone].getColumn(i,new org.joml.Vector3f()).normalize();ba[i]=m[b.bone].getColumn(i,new org.joml.Vector3f()).normalize();axes.add(aa[i]);axes.add(ba[i]);}
        for(var x:aa)for(var y:ba)axes.add(new org.joml.Vector3f(x).cross(y));
        double gap=-Double.MAX_VALUE;
        for(var axis:axes)if(axis.lengthSquared()>1e-8) {
            axis.normalize();double extent=0;
            for(int i=0;i<3;i++)extent+=Math.abs(axis.dot(aa[i]))*ah.get(i)+Math.abs(axis.dot(ba[i]))*bh.get(i);
            gap=Math.max(gap,Math.abs(axis.dot(new org.joml.Vector3f(bc).sub(ac)))-extent);
        }
        return gap;
    }
    static void chairContacts(NativeNpcModel model) {
        var parts=boxes(model);var pose=new NativeNpcPose(model);int count=0;
        for(var name:new String[]{"sit_enter","sit_play","sit_exit"}) {
            double length=model.clips().get("animation.sam."+name).length();
            for(double t=0;t<=length;t+=.025) {
                pose.reset();pose.apply("animation.sam."+name,t);var matrices=pose.matrices();
                for(var arm:parts)if(arm.name.startsWith("forearm_")||arm.name.startsWith("elbow_fill_"))
                    for(var solid:parts)if(solid.name.equals("jacket_edges")||solid.name.equals("torso")||solid.name.startsWith("thigh_")||solid.name.startsWith("shin_"))
                        require(gap(arm,solid,matrices)>-.035,"chair "+name+" at "+t+": "+arm.name+" / "+solid.name+" penetration "+gap(arm,solid,matrices));
                count++;
            }
        }
        System.out.println("PASS: chair sleeve/jacket/leg volume contacts, "+count+" samples");
    }
    public static void main(String[] args) throws Exception {
        require(SamActivity.fromAnimation("sam_work")==SamActivity.SWEEP,"work alias");
        require(SamActivity.fromAnimation("sam_skateboarding")==SamActivity.SKATEBOARD,"skate alias");
        require(SamSchedulePolicy.select("fall",11,2,"rain",0,0,42,67).equals("fall_11"),"clinic before rain");
        require(SamSchedulePolicy.select("fall",9,2,"rain",8,0,42,65).equals("spring"),"six hearts is a threshold");
        require(SamSchedulePolicy.select("summer",23,2,"rain",0,7,42,51).equals("spring"),"Penny condition uses spring fallback");
        require(SamSchedulePolicy.select("winter",9,2,"rain",0,0,42,93).equals("9"),"date before rain");
        require(SamSchedulePolicy.select("summer",11,1,"greenrain",0,0,42,39).equals("GreenRain"),"first-year green rain");
        require(SamSchedulePolicy.select("spring",5,1,"sun",0,0,42,5).equals("Fri"),"Friday");
        var rains=new java.util.HashSet<String>();
        for(int day=1;day<=112;day++) {
            String a=SamSchedulePolicy.select("spring",2,1,"rain",0,0,42,day);
            rains.add(a);require(a.equals(SamSchedulePolicy.select("spring",2,1,"rain",0,0,42,day)),"stable daily rain");
        }
        require(rains.size()==2,"both rainy schedules reachable");
        int samples=0;
        for(var action:SamActivity.values()) {
            var model=new Gson().fromJson(Files.readString(Path.of(args[0],action.asset()+".json")),NativeNpcModel.class);
            require(model.clips().containsKey("animation.sam.idle"),action+" exit idle");
            require(model.clips().containsKey(action.playClip()) && model.clips().containsKey(action.holdClip()),action+" clips");
            var pose=new NativeNpcPose(model);var reference=new NativeNpcPose(model);
            if(action==SamActivity.SIT)chairContacts(model);
            if(action.supported()) {
                require(Math.abs(model.clips().get(action.enterClip()).length()*20-action.enterTicks())<.001,"enter duration");
                require(Math.abs(model.clips().get(action.exitClip()).length()*20-action.exitTicks())<.001,"exit duration");
            }
            for(int at:new int[]{action.enterTicks(),action.enterTicks()+3,action.enterTicks()+97}) {
                var event=new CompoundTag();event.putLong("start",1000);event.putLong("exit",1000+at);
                NativeSamSchedulePose.apply(pose,reference,model,action,event,1000+at,1.25);
                var before=snapshot(pose);
                event.remove("exit");
                NativeSamSchedulePose.apply(pose,reference,model,action,event,1000+at,1.25);
                require(distance(before,snapshot(pose))<.0001,action+" interruption keeps exact pose");
                event.putLong("exit",1000+at);
                Matrix4f[] previous=null;
                for(double t=0;t<=action.exitTicks()+.001;t+=.25) {
                    NativeSamSchedulePose.apply(pose,reference,model,action,event,1000+at+t,1.25+t/20);
                    var current=snapshot(pose);if(previous!=null) require(distance(previous,current)<8,action+" exit discontinuity");
                    previous=current;samples++;
                }
                reference.reset();reference.apply("animation.sam.idle",1.25+action.exitTicks()/20.0);
                if(action.supported()) {
                    reference.addPosition("root",action==SamActivity.SLEEP?-14:0,0,action==SamActivity.SLEEP?10:-13);
                    if(action==SamActivity.SLEEP)reference.addRotation("root",0,90,0);
                }
                // Hidden prop transforms are irrelevant; body must match the walking/idle owner at release.
                var a=snapshot(pose);var b=snapshot(reference);
                for(int i=0;i<model.bones().size();i++) {
                    String name=model.bones().get(i).name();
                    if(java.util.Set.of("root","body","head","leg_left","leg_right","arm_left","arm_right").contains(name))
                        require(distance(new Matrix4f[]{a[i]},new Matrix4f[]{b[i]})<.002,action+" release "+name);
                }
            }
        }
        System.out.println("PASS: schedule precedence, heart thresholds, rain stability, seven activities, "+samples+" transition samples and release poses");
    }
}
