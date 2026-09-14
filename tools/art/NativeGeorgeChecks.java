import com.google.gson.Gson;
import com.stardew.craft.client.npcnative.*;
import com.stardew.craft.npc.attention.*;
import org.joml.Vector3f;
import java.nio.file.*;
import java.util.*;

/** Wheelchair-specific body/arm coordination, actual rolling geometry and seated support checks. */
public final class NativeGeorgeChecks {
    static void near(double a,double b,double e,String label) { NativeCharacterChecks.near(a,b,e,label); }
    static int bone(NativeNpcModel m,String n) { return NativeCharacterChecks.bone(m,n); }
    static Map<String,Object> frame(NativeNpcPose p,double distance) {
        return Map.of("matrices",NativeCharacterChecks.matrices(p),"distance",distance);
    }
    static void contacts(NativeNpcModel m,NativeNpcPose p) {
        var root=p.matrices()[bone(m,"root")];
        for(String n:List.of("leg_right","leg_left","shin_right","shin_left","wheelchair")) {
            float[] a=p.matrices()[bone(m,n)].get(new float[16]),b=root.get(new float[16]);
            for(int i=0;i<16;i++)near(a[i],b[i],1e-5,"seated support "+n);
        }
        var axle=root.transformPosition(new Vector3f(0,7,2));
        near(axle.x,0,1e-5,"rear axle pivot X");near(axle.y,7,1e-5,"rear axle height");near(axle.z,2,1e-5,"rear axle pivot Z");
        NativeCharacterChecks.fixedEyes(m,p);
    }
    static List<Vector3f> vertices(NativeNpcModel m,NativeNpcPose p,String part) {
        var out=new ArrayList<Vector3f>();var matrices=p.matrices();var surface=p.surfaceVertices(matrices);
        for(int qi=0;qi<m.quads().size();qi++) {
            var q=m.quads().get(qi);
            if(!part.equals(q.sourcePart()))continue;
            for(int vi=0;vi<4;vi++) {
                var v=q.vertices()[vi];
                out.add(surface!=null&&surface[qi]!=null?new Vector3f(surface[qi][vi])
                        :matrices[q.bone()].transformPosition(new Vector3f(v[0],v[1],v[2])));
            }
        }
        if(out.isEmpty())throw new AssertionError("Missing "+part);
        return out;
    }
    static boolean intersects(List<Vector3f> a,List<Vector3f> b) {
        var axes=new ArrayList<Vector3f>();
        var aa=new ArrayList<Vector3f>();var bb=new ArrayList<Vector3f>();
        for(var entry:List.of(Map.entry(a,aa),Map.entry(b,bb)))for(int j=0;j<entry.getKey().size();j+=4) {
            var v=entry.getKey();var edge=new Vector3f(v.get(j+1)).sub(v.get(j));
            var other=new Vector3f(v.get(j+3)).sub(v.get(j));
            if(edge.lengthSquared()>1e-8)entry.getValue().add(edge.normalize());
            var normal=new Vector3f(edge).cross(other);if(normal.lengthSquared()>1e-8)axes.add(normal.normalize());
        }
        for(var x:aa)for(var y:bb){var cross=new Vector3f(x).cross(y);if(cross.lengthSquared()>1e-8)axes.add(cross.normalize());}
        for(var axis:axes) {
            double amin=Double.POSITIVE_INFINITY,amax=-amin,bmin=amin,bmax=-amin;
            for(var v:a){amin=Math.min(amin,v.dot(axis));amax=Math.max(amax,v.dot(axis));}
            for(var v:b){bmin=Math.min(bmin,v.dot(axis));bmax=Math.max(bmax,v.dot(axis));}
            if(Math.min(amax,bmax)-Math.max(amin,bmin)<.02)return false;
        }
        return true;
    }
    static void clearance(NativeNpcModel m,NativeNpcPose p,double t) {
        if(intersects(vertices(m,p,"jacket_body"),vertices(m,p,"back_canvas")))
            throw new AssertionError("Chest intersects back support at "+t);
        // Limb names now follow anatomical bone sides; original chair part suffixes are preserved.
        for(String side:List.of("left","right")) {
            // Both halves now include their continuous weighted elbow rows.
            for(String limb:List.of("upper_arm_","forearm_")) {
            var arm=vertices(m,p,limb+side);
            for(String part:List.of("armrest_pad_","armrest_rail_","back_canvas")) {
                var obstacle=vertices(m,p,part.endsWith("_")?part+(side.equals("right")?"left":"right"):part);
                if(intersects(arm,obstacle))throw new AssertionError("Arm collision "+limb+side+" "+part+" phase "+t);
            }
            }
        }
    }
    static boolean handOnRim(NativeNpcModel m,NativeNpcPose p,String side) {
        var hand=new ArrayList<Vector3f>();var matrices=p.matrices();
        for(var q:m.quads())if(q.sourcePart().equals("forearm_"+side))for(var v:q.vertices())
            hand.add(matrices[q.bone()].transformPosition(new Vector3f(v[0],Math.min(v[1],12),v[2])));
        for(String name:m.quads().stream().map(NativeNpcModel.Quad::sourcePart).distinct().toList())
            if(name.startsWith("handrim_"+side+"_")&&intersects(hand,vertices(m,p,name)))return true;
        return false;
    }
    public static void main(String[] args)throws Exception {
        var m=new Gson().fromJson(Files.readString(Path.of(args[0])),NativeNpcModel.class);
        var p=new NativeNpcPose(m);var out=new LinkedHashMap<String,Object>();out.put("fps",25);
        var parity=new ArrayList<Object>();
        for(var name:m.clips().keySet())for(double t:new double[]{0,.031,.143,.7}) {
            double time=t*m.clips().get(name).length();p.reset();p.apply(name,time);
            parity.add(Map.of("clip",name.substring(name.lastIndexOf('.')+1),"time",time,"matrices",NativeCharacterChecks.matrices(p)));
        }
        out.put("parity",parity);
        int grip=0,gripTotal=0;
        double headTravel=0,groundMin=0,groundMax=0,elbowMin=180,elbowMax=0,authorIkError=0;
        Vector3f firstHead=null;
        for(int i=0;i<=240;i++) {
            double t=i/240.;p.reset();p.apply("animation.george.idle",t*10);
            p.apply("animation.george.walk",t);
            contacts(m,p);clearance(m,p,t);
            var head=p.boneMatrix("head").transformPosition(new Vector3f(0,25,1));
            if(firstHead==null)firstHead=new Vector3f(head);headTravel=Math.max(headTravel,head.distance(firstHead));
            var local=new org.joml.Matrix4f(p.boneMatrix("arm_left")).invert().mul(p.boneMatrix("forearm_left"));
            double bend=Math.toDegrees(Math.atan2(local.m12(),local.m22()));
            elbowMin=Math.min(elbowMin,bend);elbowMax=Math.max(elbowMax,bend);
            for(String side:List.of("right","left"))for(String prefix:List.of("tire_","caster_tread_")) {
                double bottom=Double.POSITIVE_INFINITY;
                var matrices=p.matrices();
                for(var quad:m.quads())if(quad.sourcePart().startsWith(prefix+side+"_"))for(var v:quad.vertices())
                    bottom=Math.min(bottom,matrices[quad.bone()].transformPosition(new Vector3f(v[0],v[1],v[2])).y);
                if(!Double.isFinite(bottom)||bottom<-.44||bottom>.08)throw new AssertionError("Original faceted wheel envelope "+bottom);
                groundMin=Math.min(groundMin,bottom);groundMax=Math.max(groundMax,bottom);
            }
            if(i<240) {
                var production=new NativeNpcPose(m);production.apply("animation.george.idle",t*10);
                var wheel=new NativeWheelchairClock.Wheel(-Math.toDegrees(16./7)*t,t,1);
                NativeGeorgePose.locomotion(production,new NativeWheelchairClock.Sample(wheel,wheel,0,0,-Math.toDegrees(8)*t,-Math.toDegrees(8)*t));
                contacts(m,production);clearance(m,production,t);
                for(String side:List.of("right","left")) {
                    int sign=side.equals("right")?-1:1;
                    var hand=production.boneMatrix("forearm_"+side).transformPosition(new Vector3f(sign*5.5F,10.8F,1));
                    authorIkError=Math.max(authorIkError,hand.distance(NativeGeorgePose.handTarget(t,sign)));
                }
            }
            if(t<=.38) for(String side:List.of("right","left")) {
                gripTotal++;if(handOnRim(m,p,side))grip++;
            }
            if(t>.57&&t<.67) for(String side:List.of("right","left"))
                if(handOnRim(m,p,side))throw new AssertionError("Recovery still gripping "+side);
        }
        near(grip,gripTotal,0,"hands contact rim throughout push");
        if(headTravel<2 || elbowMax-elbowMin<25)throw new AssertionError("Propulsion lost body/neck or elbow motion");
        near(authorIkError,0,.002,"production hand contact IK");
        for(String side:List.of("right","left"))for(String prefix:List.of("tire_","handrim_","caster_tread_")) {
            String expected=(prefix.equals("caster_tread_")?"caster_spin_":"wheel_spin_")+side;
            for(var q:m.quads())if(q.sourcePart().startsWith(prefix+side+"_")&&!m.bones().get(q.bone()).name().equals(expected))
                throw new AssertionError("Stationary wheel surface "+q.sourcePart());
        }
        System.out.println("Push contact samples: "+grip+"/"+gripTotal);
        System.out.println("Head excursion="+headTravel+", elbow range="+elbowMin+".."+elbowMax+", original wheel envelope="+groundMin+".."+groundMax+", IK error="+authorIkError);
        for(int i=0;i<=250;i++) {
            p.reset();p.apply("animation.george.idle",i/25.);clearance(m,p,i/25.);
            for(String side:List.of("right","left")) {
                int sign=side.equals("right")?-1:1;
                var hand=p.boneMatrix("forearm_"+side).transformPosition(new Vector3f(sign*5.5F,10.8F,1));
                near(hand.distance(new Vector3f(sign*4.5F,15.6F,-6.35F)),0,.002,"resting hand stable through breath");
                var elbow=p.boneMatrix("arm_"+side).transformPosition(new Vector3f(sign*5.5F,15,1));
                if(sign*elbow.x<4.5 || elbow.z< -3.5 || elbow.y>17)
                    throw new AssertionError("Resting elbow twists inward or lifts: "+elbow);
                if(handOnRim(m,p,side))throw new AssertionError("Idle hand still on handrim");
            }
        }
        // Interrupted preparation, short motion, restart during settling, and full stop.
        // Every sampled pose uses the same stateful clock and arm solver as the renderer.
        double transitionSpeed=0;
        for(int fps:new int[]{20,60,144})for(double direction:new double[]{-1,1}) {
            var c=new NativeWheelchairClock();Vector3f previous=null;double distance=0;
            for(int i=0;i<=fps*4;i++) {
                double t=i/(double)fps;
                boolean preparing=t>.10&&t<.24;
                if(t>.6&&t<1.05 || t>1.22&&t<2.25)distance+=direction*.8/fps;
                var sample=c.sample(t,0,distance,0,true,preparing);
                if(t<.6)near(sample.left().angle(),0,0,"preparation must not rotate wheels");
                p.reset();p.apply("animation.george.idle",t);NativeGeorgePose.locomotion(p,sample);
                contacts(m,p);clearance(m,p,t);
                var hand=p.boneMatrix("forearm_left").transformPosition(new Vector3f(5.5F,10.8F,1));
                if(previous!=null)transitionSpeed=Math.max(transitionSpeed,hand.distance(previous)*fps);
                previous=hand;
                if(t>3)near(sample.weight(),0,0,"hands settle after stopping");
            }
        }
        for(double boundary:new double[]{0,.15,.85,1})for(double phase:new double[]{0,.2,.4,.6,.8}) {
            List<Vector3f> before=null;
            for(double weight:new double[]{Math.max(0,boundary-.00001),Math.min(1,boundary+.00001)}) {
                var wheel=new NativeWheelchairClock.Wheel(0,phase,weight);
                p.reset();p.apply("animation.george.idle",0);
                NativeGeorgePose.locomotion(p,new NativeWheelchairClock.Sample(wheel,wheel,0,0,0,0));
                var points=vertices(m,p,"forearm_left");
                if(before!=null)for(int j=0;j<points.size();j++)
                    near(points.get(j).distance(before.get(j)),0,.01,"continuous transfer boundary "+boundary);
                before=points;
            }
        }
        System.out.println("Resting elbow direction, interrupted/restarted transfers and preparation checks passed; max hand speed="+transitionSpeed);
        for(double yaw:new double[]{-180,-110,-36,0,36,110,180}) {
            var turnClocks=new NativeWheelchairClock[5];
            for(int k=0;k<5;k++)turnClocks[k]=new NativeWheelchairClock();
            for(int i=0;i<150;i++) {
            double t=i/25.;
            for(double weight:new double[]{0,.25,.5,.75,1}) {
                p.reset();p.apply("animation.george.idle",t);
                var s=NpcWheelchairAttentionMotion.sample(t,yaw,12,32);
                NativeGeorgePose.locomotion(p,turnClocks[(int)Math.round(weight*4)].sample(t,0,0,-s.bodyYaw()*weight,true,NativeGeorgePose.preparesTurn(t,yaw)));
                NativeGeorgePose.attention(p,s,weight);contacts(m,p);clearance(m,p,t);
            }
            double ready=NpcDialogueMotion.readyTime(yaw,-1);
            double before=NpcDialogueMotion.motionTime(ready+2,yaw,-1,-1);
            double after=NpcDialogueMotion.motionTime(ready+2,yaw,ready+2,-1);
            var a=NpcWheelchairAttentionMotion.sample(before,yaw,12,32);
            var b=NpcWheelchairAttentionMotion.sample(after,yaw,12,32);
            near(a.bodyYaw(),b.bodyYaw(),1e-8,"dialogue release chair continuity");
            near(a.headYaw(),b.headYaw(),1e-8,"dialogue release head continuity");
        }
        }
        for(String mode:List.of("idle","walk","attention","dialogue")) {
            var frames=new ArrayList<Object>();var clock=new NativeWheelchairClock();
            int count=mode.equals("idle")?250:mode.equals("dialogue")?225:150;
            for(int i=0;i<count;i++) {
                double t=i/25.,distance=0,turn=0;boolean preparing=false;NpcAttentionMotion.Sample attention=null;p.reset();p.apply("animation.george.idle",t);
                if(mode.equals("attention")||mode.equals("dialogue")) {
                    double mt=mode.equals("dialogue")?NpcDialogueMotion.motionTime(t,145,4.2,-1):t;
                    var s=NpcWheelchairAttentionMotion.sample(mt,145,8,48);
                    attention=s;turn=s.bodyYaw();preparing=NativeGeorgePose.preparesTurn(mt,145);
                    if(s.blink()>=0)p.apply("animation.george.blink",s.blink());
                }
                if(mode.equals("walk"))distance=t<.5?0:t<4.5?(t-.5)*.8:3.2;
                var wheels=clock.sample(t,0,distance,-turn,true,preparing);
                NativeGeorgePose.locomotion(p,wheels);
                if(attention!=null)NativeGeorgePose.attention(p,attention,1);
                if(mode.equals("idle")&&t>=2.1&&t<=2.33)p.apply("animation.george.blink",t-2.1);
                contacts(m,p);clearance(m,p,t);frames.add(frame(p,distance));
            }
            out.put(mode,frames);
        }
        for(int fps:new int[]{20,25,60,144}) {
            var c=new NativeWheelchairClock();NativeWheelchairClock.Sample s=null;
            for(int i=0;i<=fps;i++)s=c.sample(i/(double)fps,0,i/(double)fps,0,true);
            near(s.left().angle(),-Math.toDegrees(16./7),1e-8,"rolling distance fps "+fps);
            near(s.right().angle(),s.left().angle(),1e-8,"straight differential");
            var duplicate=c.sample(1,0,1,0,true);if(!s.equals(duplicate))throw new AssertionError("duplicate render pass");
            c=new NativeWheelchairClock();for(int i=0;i<=fps;i++)s=c.sample(i/(double)fps,0,0,90.*i/fps,true);
            near(s.right().angle(),-s.left().angle(),1e-7,"turn differential");
            near(Math.abs(s.left().angle()),90*8.5/7,1e-7,"turn arc");
        }
        if(args.length>1){var dest=Path.of(args[1]);Files.createDirectories(dest.getParent());Files.writeString(dest,new Gson().toJson(out));}
        System.out.println("George: hip/head/elbow propulsion, full-wheel rolling, seat/feet/face, limb clearance, axle pivot, differential distance and multi-FPS checks passed.");
    }
}
