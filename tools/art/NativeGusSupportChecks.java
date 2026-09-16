import com.google.gson.Gson;
import com.stardew.craft.client.npcnative.*;
import org.joml.Vector3f;
import java.nio.file.*;
import java.util.*;

/** Export the same continuous skin and approach blend used by the generic activity renderer. */
public final class NativeGusSupportChecks {
    static void pose(NativeNpcPose p,NativeNpcPose ref,NativeNpcModel m,String variant,String stage,double t) {
        String prefix="animation.gus."+variant;double length=m.clips().get(prefix+"_"+stage).length();
        p.reset();p.apply("animation.gus.idle",0);
        if(stage.equals("exit")) {p.apply(prefix+"_play",0);p.blend(prefix+"_exit",t,smooth(t/.2));}
        else p.apply(prefix+"_"+stage,t);
        double standing=stage.equals("enter")?1-smooth(t/.2):stage.equals("exit")?smooth((t-length+.2)/.2):0;
        NativeActivityStandingPose.blend(p,ref,m,"animation.gus.idle",0,prefix+"_enter",true,standing);
    }
    static double smooth(double x){x=Math.clamp(x,0,1);return x*x*x*(10+x*(-15+6*x));}
    public static void main(String[] args)throws Exception {
        var gson=new Gson();var dir=Path.of(args[0]);
        for(String variant:List.of("sit","sleep")) {
            var m=gson.fromJson(Files.readString(dir.resolve("gus_"+variant+".json")),NativeNpcModel.class);
            var p=new NativeNpcPose(m);var ref=new NativeNpcPose(m);var frames=new ArrayList<Map<String,Object>>();
            NativeJointSkinChecks.inspect(m,"gus_"+variant);int samples=0;float maxStep=0;float[][] previous=null;
            for(String stage:List.of("enter","play","exit")) {
                String clip="animation.gus."+variant+"_"+stage;double length=m.clips().get(clip).length();previous=null;
                for(int i=0;i<=Math.round(length*120);i++) {
                    double t=i/120.;pose(p,ref,m,variant,stage,t);var matrices=p.matrices();var surface=p.surfaceVertices(matrices);
                    var points=new ArrayList<float[]>();
                    for(int j=0;j<m.quads().size();j++) {
                        var q=m.quads().get(j);
                        for(int k=0;k<4;k++) {
                            var a=surface!=null && surface[j]!=null?new Vector3f(surface[j][k]):matrices[q.bone()].transformPosition(new Vector3f(q.vertices()[k]));
                            if(!a.isFinite())throw new AssertionError("Nonfinite surface "+clip);
                            if(a.y<-.15)throw new AssertionError("Below floor "+clip+"@"+t+" "+q.sourcePart()+" "+a);
                            boolean cushion=variant.equals("sit") ? a.x>-16.4 && a.x<16.4 && a.z>-6.9 && a.z<7.9 && a.y>5.1 && a.y<8.9
                                    : a.x>-9.05 && a.x<9.05 && a.z>-6.05 && a.z<21.05 && a.y>2 && a.y<8.05;
                            if(cushion)throw new AssertionError("Inside cushion "+clip+"@"+t+" "+q.sourcePart()+" "+a);
                            points.add(new float[]{a.x,a.y,a.z});
                        }
                    }
                    var current=points.toArray(float[][]::new);
                    if(previous!=null)for(int j=0;j<current.length;j++)maxStep=Math.max(maxStep,new Vector3f(current[j]).distance(new Vector3f(previous[j])));
                    previous=current;samples++;
                    if(i%6==0 && i<Math.round(length*120)) {
                        var deformed=new LinkedHashMap<Integer,float[][]>();
                        if(surface!=null)for(int q=0;q<surface.length;q++)if(surface[q]!=null)deformed.put(q,Arrays.stream(surface[q]).map(float[]::clone).toArray(float[][]::new));
                        frames.add(Map.of("clip",clip,"time",t,"matrices",Arrays.stream(matrices).map(a->a.get(new float[16])).toList(),"surface",deformed));
                    }
                }
            }
            if(maxStep>1.2)throw new AssertionError("Discontinuous support pose "+variant+" "+maxStep);
            if(args.length>1){var out=Path.of(args[1]).resolve("gus-"+variant);Files.createDirectories(out);Files.writeString(out.resolve("poses.json"),gson.toJson(Map.of("clip","gus_"+variant+" enter/play/exit","duration",frames.size()/20.,"fps",20,"frames",frames)));}
            System.out.println("Gus "+variant+": "+samples+" production poses; welded joints, finite surfaces and floor clearance; max 1/120s travel="+maxStep);
        }
    }
}
