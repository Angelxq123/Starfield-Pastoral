import com.google.gson.Gson;
import com.stardew.craft.client.npcnative.*;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import java.nio.file.*;
import java.util.*;

/** Production matrices and weighted surfaces, including actual cup-interior contact. */
public final class NativeGusActivityChecks {
    private static float[] bounds(NativeNpcModel m,String part,Matrix4f transform) {
        float[] b={Float.POSITIVE_INFINITY,Float.POSITIVE_INFINITY,Float.POSITIVE_INFINITY,
                Float.NEGATIVE_INFINITY,Float.NEGATIVE_INFINITY,Float.NEGATIVE_INFINITY};
        for(var q:m.quads())if(q.sourcePart().equals(part))for(var v:q.vertices()) {
            var p=transform.transformPosition(new Vector3f(v[0],v[1],v[2]));
            for(int a=0;a<3;a++){b[a]=Math.min(b[a],p.get(a));b[a+3]=Math.max(b[a+3],p.get(a));}
        }
        if(!Float.isFinite(b[0]))throw new AssertionError("Missing prop part "+part);
        return b;
    }
    private static void clearRim(NativeNpcModel m,Matrix4f[] matrices,int cup,int cloth,String label) {
        var relative=new Matrix4f(matrices[cup]).invert().mul(matrices[cloth]);
        for(String fabric:List.of("cloth_insert","cloth_grip_fold")) {
            var a=bounds(m,fabric,relative);
            for(String wall:List.of("base","front","back","left","right")) {
                var b=bounds(m,"shaker_"+wall,new Matrix4f());boolean overlap=true;
                for(int axis=0;axis<3;axis++)overlap &= Math.min(a[axis+3],b[axis+3])-Math.max(a[axis],b[axis])>.025;
                if(overlap)throw new AssertionError("Cloth crosses shaker "+wall+" during "+label+" part="+fabric+" bounds="+Arrays.toString(a));
            }
        }
    }
    private static int bone(NativeNpcModel m,String name) {
        for(int i=0;i<m.bones().size();i++)if(m.bones().get(i).name().equals(name))return i;
        throw new AssertionError("Missing bone "+name);
    }
    public static void main(String[] args)throws Exception {
        var gson=new Gson();var m=gson.fromJson(Files.readString(Path.of(args[0])),NativeNpcModel.class);
        var p=new NativeNpcPose(m);int cup=bone(m,"clean_cup"),cloth=bone(m,"clean_cloth");
        int joints=NativeJointSkinChecks.inspect(m,"gus_clean");
        for(String side:List.of("left","right")) {
            int upper=bone(m,"arm_"+side),lower=bone(m,"forearm_"+side);
            if(m.quads().stream().noneMatch(q->q.skin()!=null && q.skin().upper()==upper && q.skin().lower()==lower
                    && java.util.stream.IntStream.range(0,4).anyMatch(i->q.skin().weights()[i]>0 && q.skin().weights()[i]<1)))
                throw new AssertionError("Missing continuous elbow surface "+side);
        }
        int samples=0;float worstStep=0;var frames=new ArrayList<Map<String,Object>>();
        Vector3f previous=null;
        for(int i=0;i<=384;i++) {
            double time=i/120.0;p.reset();p.apply("animation.gus.clean_play",time);
            var matrices=p.matrices();p.surfaceVertices(matrices);
            var tip=matrices[cloth].transformPosition(new Vector3f(0,-2.25f,0));
            var local=new Matrix4f(matrices[cup]).invert().transformPosition(new Vector3f(tip));
            if(!(Math.abs(local.x)<1.1 && Math.abs(local.z)<1.1 && local.y>-1.55 && local.y<1.5))
                throw new AssertionError("Cloth is not inside shaker cavity at "+time+": "+local);
            if(previous!=null)worstStep=Math.max(worstStep,previous.distance(tip));previous=new Vector3f(tip);
            for(var matrix:matrices)if(!matrix.isFinite())throw new AssertionError("Nonfinite pose");
            samples++;
        }
        if(worstStep>.12)throw new AssertionError("Cloth contact discontinuity: "+worstStep);
        for(String name:List.of("enter","play","exit")) {
            var clip="animation.gus.clean_"+name;var length=m.clips().get(clip).length();
            for(int i=0;i<=Math.round(length*120);i++) {
                p.reset();p.apply(clip,i/120.0);clearRim(m,p.matrices(),cup,cloth,clip+"@"+i/120.0);
            }
        }
        for(String name:List.of("enter","play","exit")) {
            String clip="animation.gus.clean_"+name;
            if(!m.clips().containsKey(clip))throw new AssertionError("Missing "+clip);
            double length=m.clips().get(clip).length();
            for(int i=0;i<Math.round(length*20);i++) {
                double t=i/20.0;p.reset();p.apply(clip,t);var matrices=p.matrices();var surface=p.surfaceVertices(matrices);
                var deformed=new LinkedHashMap<Integer,float[][]>();
                if(surface!=null)for(int q=0;q<surface.length;q++)if(surface[q]!=null)
                    deformed.put(q,Arrays.stream(surface[q]).map(float[]::clone).toArray(float[][]::new));
                frames.add(Map.of("clip",clip,"time",t,"matrices",Arrays.stream(matrices).map(a->a.get(new float[16])).toList(),"surface",deformed));
            }
        }
        if(args.length>1) {
            var out=Path.of(args[1]);Files.createDirectories(out.getParent());
            Files.writeString(out,gson.toJson(Map.of("clip","gus_clean enter/play/exit","duration",6.4,"fps",20,"frames",frames)));
        }
        System.out.println("Gus cleaning: "+samples+" loop poses; "+joints+" welded joints; whole cloth clear of walls throughout enter/play/exit; max 1/120s cloth travel="+worstStep);
    }
}
