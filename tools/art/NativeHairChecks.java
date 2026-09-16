import com.stardew.craft.client.npcnative.*;
import com.stardew.craft.npc.attention.*;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import java.util.*;

/** Checks approved shoulder-length hair against the actual posed clothing. */
public final class NativeHairChecks {
    record Shape(String name, int bone, List<Vector3f> vertices, List<Vector3f> axes) {
        Shape posed(Matrix4f[] matrices) {
            var matrix=matrices[bone];
            return new Shape(name,bone,vertices.stream().map(v->matrix.transformPosition(new Vector3f(v))).toList(),
                    axes.stream().map(v->matrix.transformDirection(new Vector3f(v)).normalize()).toList());
        }
    }
    static void axis(List<Vector3f> axes,Vector3f v) {
        if(v.lengthSquared()<1e-8)return;
        v.normalize();
        if(axes.stream().noneMatch(a->Math.abs(a.dot(v))>.99999f))axes.add(v);
    }
    static List<Shape> shapes(NativeNpcModel model) {
        var parts=new LinkedHashMap<String,List<NativeNpcModel.Quad>>();
        for(var q:model.quads())if(q.sourcePart().startsWith("pigtail_")||q.sourcePart().startsWith("tie_")
                ||q.sourcePart().startsWith("arm_")||q.sourcePart().startsWith("sleeve_"))
            parts.computeIfAbsent(q.sourcePart(),n->new ArrayList<>()).add(q);
        var result=new ArrayList<Shape>();
        for(var part:parts.entrySet()) {
            var vertices=new ArrayList<Vector3f>();var axes=new ArrayList<Vector3f>();
            for(var quad:part.getValue()) {
                var q=quad.vertices();
                for(var v:q) {
                    var point=new Vector3f(v[0],v[1],v[2]);
                    if(vertices.stream().noneMatch(p->p.distanceSquared(point)<1e-8))vertices.add(point);
                }
                var a=new Vector3f(q[1][0]-q[0][0],q[1][1]-q[0][1],q[1][2]-q[0][2]);
                var b=new Vector3f(q[3][0]-q[0][0],q[3][1]-q[0][1],q[3][2]-q[0][2]);
                axis(axes,new Vector3f(a).cross(b));axis(axes,a);axis(axes,b);
            }
            result.add(new Shape(part.getKey(),part.getValue().getFirst().bone(),vertices,axes));
        }
        if(result.stream().filter(s->s.name.startsWith("pigtail_")||s.name.startsWith("tie_")).count()!=6 || result.size()<10)throw new AssertionError("Missing hair or articulated arm surfaces");
        return result;
    }
    static boolean separated(Shape a,Shape b,Vector3f axis) {
        if(axis.lengthSquared()<1e-8)return false;
        axis.normalize();float amin=Float.POSITIVE_INFINITY,amax=Float.NEGATIVE_INFINITY;
        float bmin=Float.POSITIVE_INFINITY,bmax=Float.NEGATIVE_INFINITY;
        for(var v:a.vertices){float d=v.dot(axis);amin=Math.min(amin,d);amax=Math.max(amax,d);}
        for(var v:b.vertices){float d=v.dot(axis);bmin=Math.min(bmin,d);bmax=Math.max(bmax,d);}
        return Math.min(amax-bmin,bmax-amin)<=.002f;
    }
    static boolean intersects(Shape a,Shape b) {
        for(var axis:a.axes)if(separated(a,b,new Vector3f(axis)))return false;
        for(var axis:b.axes)if(separated(a,b,new Vector3f(axis)))return false;
        for(var x:a.axes)for(var y:b.axes)if(separated(a,b,new Vector3f(x).cross(y)))return false;
        return true;
    }
    static void check(List<Shape> shapes,NativeNpcPose pose,String label) {
        var matrices=pose.matrices();var posed=shapes.stream().map(s->s.posed(matrices)).toList();
        for(var hair:posed)if(hair.name.startsWith("pigtail_")||hair.name.startsWith("tie_"))
            for(var arm:posed)if(arm.name.startsWith("arm_")||arm.name.startsWith("sleeve_"))
                if(intersects(hair,arm))throw new AssertionError(label+": "+hair.name+" intersects "+arm.name);
    }
    static void verify(NativeNpcModel model,String id) {
        var shapes=shapes(model);var pose=new NativeNpcPose(model);var rig=model.profile().attentionRig();int count=0;
        double cycle=model.clips().get("animation."+id+".idle").length();
        for(int i=0;i<=Math.ceil(cycle*60);i++) {
            pose.reset();pose.apply("animation."+id+".idle",i/60.);check(shapes,pose,"idle "+i);count++;
        }
        for(double weight:new double[]{0,.25,.5,.75,1})for(int i=0;i<=240;i++) {
            pose.reset();pose.apply("animation."+id+".idle",i/60.);
            pose.blend("animation."+id+".walk",i/240.,weight);check(shapes,pose,"walk/blend "+i+"/"+weight);count++;
        }
        for(double yaw:new double[]{-180,-130,-90,-50,-20,0,20,50,90,130,180})
            for(double pitch:new double[]{-32,0,32})for(double phase:new double[]{0,1.6,4.2})
                for(int i=0;i<=Math.ceil(NpcAttentionMotion.duration(yaw)*60);i++) {
                    double t=i/60.;pose.reset();pose.apply("animation."+id+".idle",phase+t);
                    NativeSamAttentionPose.apply(pose,NpcAttentionMotion.sample(t,yaw,pitch,48,rig),1,rig);
                    check(shapes,pose,"turn "+yaw+"/"+pitch+"/"+phase+"/"+t);count++;
                }
        System.out.println("PASS "+id+" hair: "+count+" poses, six fixed-shape hair parts clear both sleeves/arms");
    }
    static void verifyLeah(NativeNpcModel model) {
        record Obstacle(int bone,Vector3f min,Vector3f max) {}
        var parts=new LinkedHashMap<String,List<NativeNpcModel.Quad>>();
        for(var q:model.quads())if(q.sourcePart().startsWith("arm_")||q.sourcePart().startsWith("sleeve_")
                ||q.sourcePart().equals("body_base")||q.sourcePart().equals("shirt_edges"))
            parts.computeIfAbsent(q.sourcePart(),n->new ArrayList<>()).add(q);
        var obstacles=new ArrayList<Obstacle>();
        for(var qs:parts.values()) {
            var min=new Vector3f(Float.POSITIVE_INFINITY);var max=new Vector3f(Float.NEGATIVE_INFINITY);
            for(var q:qs)for(var v:q.vertices()){var p=new Vector3f(v[0],v[1],v[2]);min.min(p);max.max(p);}
            obstacles.add(new Obstacle(qs.getFirst().bone(),min,max));
        }
        var braid=model.quads().stream().filter(q->q.sourcePart().equals("braid_surface")||q.sourcePart().equals("braid_end")).toList();
        var pose=new NativeNpcPose(model);int count=0;
        for(double weight:new double[]{0,.25,.5,.75,1})for(int i=0;i<=480;i++) {
            pose.reset();pose.apply("animation.leah.idle",i/60.);pose.blend("animation.leah.walk",i/240.,weight);
            var matrices=pose.matrices();
            for(var q:braid)for(var ob:obstacles) {
                var relative=new Matrix4f(matrices[ob.bone]).invert().mul(matrices[q.bone()]);var v=q.vertices();
                for(float u:new float[]{.05F,.5F,.95F})for(float t:new float[]{.05F,.5F,.95F}) {
                    var pt=relative.transformPosition(new Vector3f(v[0][0]+u*(v[1][0]-v[0][0])+t*(v[3][0]-v[0][0]),
                            v[0][1]+u*(v[1][1]-v[0][1])+t*(v[3][1]-v[0][1]),v[0][2]+u*(v[1][2]-v[0][2])+t*(v[3][2]-v[0][2])));
                    if(pt.x>ob.min.x+.002 && pt.x<ob.max.x-.002 && pt.y>ob.min.y+.002 && pt.y<ob.max.y-.002 && pt.z>ob.min.z+.002 && pt.z<ob.max.z-.002)
                        throw new AssertionError("Leah braid enters posed clothing "+i+"/"+weight);
                }
            }
            count++;
        }
        System.out.println("PASS leah braid: "+count+" idle/walk/blend poses keep visible surface outside clothing");
    }
    /** Marnie's tail is a shoulder overlay: its hidden rear face may meet clothing,
     * but the visible front surface must never disappear inside a sleeve or bodice. */
    static void verifyMarnie(NativeNpcModel model) {
        record Obstacle(int bone, Vector3f min, Vector3f max) {}
        var obstacles=new ArrayList<Obstacle>();
        for(String name:List.of("bodice","arm_left","arm_right","sleeve_left","sleeve_right")) {
            var qs=model.quads().stream().filter(q->name.equals(q.sourcePart())).toList();
            var min=new Vector3f(Float.POSITIVE_INFINITY);var max=new Vector3f(Float.NEGATIVE_INFINITY);
            for(var q:qs)for(var v:q.vertices()){var p=new Vector3f(v[0],v[1],v[2]);min.min(p);max.max(p);}
            obstacles.add(new Obstacle(qs.getFirst().bone(),min,max));
        }
        var front=model.quads().stream().filter(q->q.sourcePart().equals("hair_tail"))
                .filter(q->{var v=q.vertices();return new Vector3f(v[1][0]-v[0][0],v[1][1]-v[0][1],v[1][2]-v[0][2])
                    .cross(new Vector3f(v[2][0]-v[0][0],v[2][1]-v[0][1],v[2][2]-v[0][2])).z<-.001;}).findFirst().orElseThrow();
        var points=new ArrayList<Vector3f>();var v=front.vertices();
        for(float u:new float[]{.1F,.5F,.9F})for(float t:new float[]{.1F,.5F,.9F})
            points.add(new Vector3f(v[0][0]+u*(v[1][0]-v[0][0])+t*(v[3][0]-v[0][0]),
                    v[0][1]+u*(v[1][1]-v[0][1])+t*(v[3][1]-v[0][1]),v[0][2]+u*(v[1][2]-v[0][2])+t*(v[3][2]-v[0][2])));
        var pose=new NativeNpcPose(model);var rig=model.profile().attentionRig();int count=0;
        java.util.function.Consumer<String> check=label->{
            var matrices=pose.matrices();
            for(var obstacle:obstacles) {
                var relative=new Matrix4f(matrices[obstacle.bone]).invert().mul(matrices[front.bone()]);
                for(var point:points) {
                    var p=relative.transformPosition(new Vector3f(point));var lo=obstacle.min;var hi=obstacle.max;
                    if(p.x>lo.x+.005F&&p.x<hi.x-.005F&&p.y>lo.y+.005F&&p.y<hi.y-.005F&&p.z>lo.z+.005F&&p.z<hi.z-.005F)
                        throw new AssertionError("Visible Marnie hair buried in clothing: "+label+" "+p);
                }
            }
        };
        for(int i=0;i<=540;i++) {pose.reset();pose.apply("animation.marnie.idle",i/60.);check.accept("idle "+i);count++;}
        for(double weight:new double[]{.25,.5,.75,1})for(int i=0;i<=240;i++) {
            pose.reset();pose.apply("animation.marnie.idle",i/60.);pose.blend("animation.marnie.walk",i/240.,weight);check.accept("walk "+i);count++;
        }
        for(double yaw:new double[]{-180,-130,-90,-50,-20,0,20,50,90,130,180})
            for(double pitch:new double[]{-32,0,32})for(double phase:new double[]{0,1.6,4.3})
                for(int i=0;i<=Math.ceil(NpcAttentionMotion.duration(yaw)*60);i++) {
                    double t=i/60.;pose.reset();pose.apply("animation.marnie.idle",phase+t);
                    NativeSamAttentionPose.apply(pose,NpcAttentionMotion.sample(t,yaw,pitch,48,rig),1,rig);
                    check.accept("turn "+yaw+"/"+pitch+"/"+phase+"/"+t);count++;
                }
        System.out.println("PASS marnie hair: "+count+" poses keep the visible shoulder-tail face outside clothing");
    }

}
