import com.google.gson.Gson;
import com.stardew.craft.client.npcnative.NativeNpcModel;
import com.stardew.craft.client.npcnative.NativeNpcPose;
import org.joml.Matrix3f;
import org.joml.Vector3f;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Actual production pose evaluation, including both approved construction clips. */
public final class NativeConstructionChecks {
    public static void main(String[] args) throws Exception {
        var gson=new Gson();
        var model=gson.fromJson(Files.readString(Path.of(args[0])),NativeNpcModel.class);
        var pose=new NativeNpcPose(model);
        var frames=new ArrayList<Map<String,Object>>();
        if(!model.clips().keySet().equals(Set.of("animation.robin.construction","animation.robin.construction_low")))
            throw new AssertionError("Both approved clips required");
        for(var clip:model.clips().keySet()) {
            if(model.clips().get(clip).length()!=3.2)throw new AssertionError("Sound/clip duration mismatch");
            for(int frame=0;frame<64;frame++) {
                double time=frame/20.0;pose.reset();pose.apply(clip,time);
                var matrices=pose.matrices();
                for(var quad:model.quads()) {
                    var normal=new Vector3f(quad.normal());
                    if(quad.bone()>=0)matrices[quad.bone()].normal(new Matrix3f()).transform(normal).normalize();
                    if(!normal.isFinite()||Math.abs(normal.length()-1)>1e-4)throw new AssertionError("Invalid animated normal");
                }
                frames.add(Map.of("clip",clip,"time",time,"matrices",Arrays.stream(matrices).map(m->m.get(new float[16])).toList()));
            }
            for(double time:new double[]{.82,1.83}) {
                pose.reset();pose.apply(clip,time);
                var contact=pose.boneMatrix("hammer_grip").transformPosition(new Vector3f(-.5f,19,-.5f));
                System.out.println(clip+" impact "+time+": "+contact);
                if(contact.z>=-8 || contact.y<8 || contact.y>32)throw new AssertionError("Hammer no longer strikes in front");
                if(!clip.endsWith("_low") && contact.y<22)throw new AssertionError("High wall strike lost");
            }
            pose.reset();pose.apply(clip,0);var first=Arrays.stream(pose.matrices()).map(m->m.get(new float[16])).toList();
            pose.reset();pose.apply(clip,3.2);var last=pose.matrices();
            for(int i=0;i<last.length;i++)for(int j=0;j<16;j++)
                if(Math.abs(first.get(i)[j]-last[i].get(new float[16])[j])>1e-5)throw new AssertionError("Loop discontinuity");
        }
        var output=Path.of(args[1]);Files.createDirectories(output.getParent());Files.writeString(output,gson.toJson(Map.of("frames",frames)));
        System.out.println("Construction native clips, normals and contacts passed (128 frames)");
    }
}
