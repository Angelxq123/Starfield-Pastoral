import com.google.gson.Gson;
import com.stardew.craft.client.npcnative.*;
import org.joml.Vector3f;
import java.nio.file.*;
import java.util.*;

/** Saved Evelyn clips sampled through native joints, cloth and activity approach blending. */
public final class NativeEvelynActivityChecks {
    static double smooth(double x){x=Math.clamp(x,0,1);return x*x*x*(10+x*(-15+6*x));}
    public static void main(String[] args)throws Exception {
        var gson=new Gson();var dir=Path.of(args[0]);
        for(String variant:List.of("sit","sleep","garden")) {
            var m=gson.fromJson(Files.readString(dir.resolve("evelyn_"+variant+".json")),NativeNpcModel.class);
            var pose=new NativeNpcPose(m);var reference=new NativeNpcPose(m);var frames=new ArrayList<Map<String,Object>>();int count=0;float worstStep=0;String worstAt="";float deepest=0;String collision="";
            NativeJointSkinChecks.inspect(m,"evelyn_"+variant);
            for(var c:m.clips().entrySet())if(c.getKey().startsWith("animation.evelyn."+variant))for(var track:c.getValue().tracks())if(m.bones().get(track.bone()).name().startsWith("eye_")||m.bones().get(track.bone()).name().startsWith("lid_"))throw new AssertionError("Changed Evelyn's permanently closed eyes");
            for(String stage:List.of("enter","play","exit")) {
                var clip="animation.evelyn."+variant+"_"+stage;double length=m.clips().get(clip).length();float[][] previous=null;
                for(int frame=0;frame<=Math.round(length*120);frame++) {
                    double t=frame/120.;pose.reset();pose.apply("animation.evelyn.idle",0);
                    if(stage.equals("exit")){pose.apply("animation.evelyn."+variant+"_play",0);pose.blend(clip,t,smooth(t/.2));}else pose.apply(clip,t);
                    double standing=stage.equals("enter")?1-smooth(t/.2):stage.equals("exit")?smooth((t-length+.2)/.2):0;
                    NativeActivityStandingPose.blend(pose,reference,m,"animation.evelyn.idle",0,"animation.evelyn."+variant+"_enter",!variant.equals("garden"),standing);
                    var matrices=pose.matrices();var surface=pose.surfaceVertices(matrices);var points=new ArrayList<float[]>();
                    for(int i=0;i<m.quads().size();i++)for(int k=0;k<4;k++) {
                        var q=m.quads().get(i);var p=surface!=null&&surface[i]!=null?new Vector3f(surface[i][k]):matrices[q.bone()].transformPosition(new Vector3f(q.vertices()[k]));
                        if(!p.isFinite())throw new AssertionError("Nonfinite Evelyn surface "+clip);
                        float depth=0;
                        if(!q.sourcePart().startsWith("drop_")&&p.y<-.15)depth=-p.y;
                        if(variant.equals("sit")&&p.x>-7.4&&p.x<7.4&&p.z>-7.4&&p.z<7.4&&p.y>6.1&&p.y<8.9)depth=Math.max(depth,9-p.y);
                        if(variant.equals("sleep")&&p.x>-7.5&&p.x<26.5&&p.z>-6.9&&p.z<21&&p.y>2.1&&p.y<8.05)depth=Math.max(depth,8.15F-p.y);
                        // Water terminates at the soil; hidden or landing droplets are not rigid prop collisions.
                        if(variant.equals("garden")&&!q.sourcePart().startsWith("drop_")&&p.x>-7.9&&p.x<7.9&&p.z>-23.9&&p.z<-8.1&&p.y>0&&p.y<15.9)depth=Math.max(depth,16-p.y);
                        if(depth>deepest){deepest=depth;collision=clip+"@"+t+" "+q.sourcePart()+" "+p;}
                        points.add(new float[]{p.x,p.y,p.z});
                    }
                    var current=points.toArray(float[][]::new);
                    if(previous!=null)for(int i=0;i<current.length;i++) {
                        // Droplet birth/death is intentional visibility, not a body discontinuity.
                        if(m.quads().get(i/4).sourcePart().startsWith("drop_"))continue;
                        float step=new Vector3f(current[i]).distance(new Vector3f(previous[i]));if(step>worstStep){worstStep=step;worstAt=clip+"@"+t+" "+m.quads().get(i/4).sourcePart();}
                    }
                    previous=current;count++;
                    if(frame%6==0&&frame<Math.round(length*120)) {
                        var deformed=new LinkedHashMap<Integer,float[][]>();if(surface!=null)for(int i=0;i<surface.length;i++)if(surface[i]!=null)deformed.put(i,Arrays.stream(surface[i]).map(float[]::clone).toArray(float[][]::new));
                        frames.add(Map.of("clip",clip,"time",t,"matrices",Arrays.stream(matrices).map(a->a.get(new float[16])).toList(),"surface",deformed));
                    }
                }
            }
            if(args.length>1){var out=Path.of(args[1]).resolve("evelyn-"+variant);Files.createDirectories(out);Files.writeString(out.resolve("poses.json"),gson.toJson(Map.of("clip","evelyn_"+variant+" enter/play/exit","duration",frames.size()/20.,"fps",20,"frames",frames)));}
            System.out.println("Evelyn "+variant+": "+count+" native poses; max step="+worstStep+" at "+worstAt+"; furniture penetration="+deepest+" "+collision);
            if(deepest>.15)throw new AssertionError("Evelyn furniture clearance: "+collision);
            if(worstStep>1.3)throw new AssertionError("Evelyn body discontinuity: "+worstAt);
        }
    }
}
