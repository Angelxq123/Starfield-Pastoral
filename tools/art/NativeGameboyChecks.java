import com.google.gson.Gson;
import com.stardew.craft.client.npcnative.NativeNpcModel;
import com.stardew.craft.client.npcnative.NativeNpcPose;
import org.joml.Vector3f;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Offline candidate check; inputs are an explicitly compiled model and optional pose output. */
public final class NativeGameboyChecks {
    static int bone(NativeNpcModel m, String name) {
        for (int i=0;i<m.bones().size();i++) if(m.bones().get(i).name().equals(name)) return i;
        throw new AssertionError("Missing bone: "+name);
    }
    public static void main(String[] args) throws Exception {
        var gson=new Gson();
        var model=gson.fromJson(Files.readString(Path.of(args[0])),NativeNpcModel.class);
        var pose=new NativeNpcPose(model);
        var frames=new ArrayList<Map<String,Object>>();
        var feet=List.of(bone(model,"foot_left"),bone(model,"foot_right"));
        var firstFeet=new HashMap<Integer,Vector3f>();
        float min=Float.POSITIVE_INFINITY,max=Float.NEGATIVE_INFINITY,drift=0;
        float[][] first=null;
        for(int i=0;i<=800;i++) {
            pose.reset();pose.apply("animation.sam.gameboy_play",i/100.0);
            var matrices=pose.matrices();var exported=new float[matrices.length][16];
            for(int j=0;j<matrices.length;j++) matrices[j].get(exported[j]);
            if(first==null) first=exported;
            if(i==800) for(int j=0;j<matrices.length;j++) for(int k=0;k<16;k++)
                if(Math.abs(first[j][k]-exported[j][k])>1e-6) throw new AssertionError("Loop seam");
            for(int foot:feet) {
                float lowest=Float.POSITIVE_INFINITY;
                for(var quad:model.quads()) if(quad.bone()==foot) for(var v:quad.vertices())
                    lowest=Math.min(lowest,matrices[foot].transformPosition(new Vector3f(v[0],v[1],v[2])).y);
                min=Math.min(min,lowest);max=Math.max(max,lowest);
                if(Math.abs(lowest+model.profile().groundOffset())>.001) throw new AssertionError("Sole leaves ground");
                var pivot=matrices[foot].transformPosition(new Vector3f(model.bones().get(foot).origin()));
                firstFeet.putIfAbsent(foot,new Vector3f(pivot));drift=Math.max(drift,pivot.distance(firstFeet.get(foot)));
            }
            for(String side:List.of("left","right")) {
                int elbow=bone(model,"forearm_"+side),shoulder=bone(model,"arm_"+side);
                var e=matrices[elbow].transformPosition(new Vector3f(model.bones().get(elbow).origin()));
                var s=matrices[shoulder].transformPosition(new Vector3f(model.bones().get(shoulder).origin()));
                if(s.y-e.y<3) throw new AssertionError("Handheld elbow raised toward shoulder");
            }
            if(args.length>1) frames.add(Map.of("time",i/100.0,"matrices",exported));
        }
        if(drift>.001) throw new AssertionError("Foot slides: "+drift);
        pose.reset();pose.apply("animation.sam.gameboy_hold",.5);
        if(args.length>1) Files.writeString(Path.of(args[1]),gson.toJson(Map.of("frames",frames)));
        System.out.println("PASS: 801 native handheld samples; low elbows, loop seam, sole range="+min+".."+max+", foot drift="+drift);
    }
}
