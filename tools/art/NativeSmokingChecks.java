import com.google.gson.Gson;
import com.stardew.craft.client.npcnative.NativeNpcModel;
import com.stardew.craft.client.npcnative.NativeNpcPose;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Evaluate the smoking source through the production player, independent of Blockbench. */
public final class NativeSmokingChecks {
    static int bone(NativeNpcModel m,String name) {
        for(int i=0;i<m.bones().size();i++)if(m.bones().get(i).name().equals(name))return i;
        throw new AssertionError("Missing bone "+name);
    }
    record Box(Vector3f center,Vector3f[] axes,float[] half) {}
    static Box box(Matrix4f matrix,float[] lo,float[] hi) {
        var center=matrix.transformPosition(new Vector3f((lo[0]+hi[0])/2,(lo[1]+hi[1])/2,(lo[2]+hi[2])/2));
        Vector3f[] axes={matrix.transformDirection(new Vector3f(1,0,0)).normalize(),matrix.transformDirection(new Vector3f(0,1,0)).normalize(),matrix.transformDirection(new Vector3f(0,0,1)).normalize()};
        return new Box(center,axes,new float[]{(hi[0]-lo[0])/2,(hi[1]-lo[1])/2,(hi[2]-lo[2])/2});
    }
    static boolean overlaps(Box a,Box b) {
        var axes=new ArrayList<Vector3f>();axes.addAll(List.of(a.axes));axes.addAll(List.of(b.axes));
        for(var x:a.axes)for(var y:b.axes){var cross=new Vector3f(x).cross(y);if(cross.lengthSquared()>1e-8)axes.add(cross.normalize());}
        var delta=new Vector3f(a.center).sub(b.center);
        for(var axis:axes){float ra=0,rb=0;for(int i=0;i<3;i++){ra+=a.half[i]*Math.abs(axis.dot(a.axes[i]));rb+=b.half[i]*Math.abs(axis.dot(b.axes[i]));}if(Math.abs(axis.dot(delta))>=ra+rb-.01f)return false;}
        return true;
    }
    static Map<String,Object> frame(NativeNpcModel model,NativeNpcPose pose,String clip,double time) {
        pose.reset();pose.apply(clip,time);var mat=pose.matrices();
        return Map.of("clip",clip,"time",time,"matrices",Arrays.stream(mat).map(m->m.get(new float[16])).toList());
    }
    public static void main(String[] args)throws Exception {
        var gson=new Gson();var model=gson.fromJson(Files.readString(Path.of(args[0])),NativeNpcModel.class);var pose=new NativeNpcPose(model);
        String prefix="animation.sebastian.smoke_";float minSole=999,maxSole=-999,maxStep=0;int samples=0;
        var parity=new ArrayList<Map<String,Object>>();var frames=new ArrayList<Map<String,Object>>();
        for(String phase:List.of("enter","play","hold","exit")){
            String clip=prefix+phase;double length=model.clips().get(clip).length();Vector3f previous=null;
            for(int i=0;i<=Math.round(length*120);i++){
                double t=Math.min(length,i/120.0);pose.reset();pose.apply(clip,t);var mat=pose.matrices();samples++;
                for(var matrix:mat)if(!matrix.isFinite())throw new AssertionError("Nonfinite pose");
                var head=box(mat[bone(model,"head")],new float[]{-4,24,-4},new float[]{4,32,4});
                var hand=box(mat[bone(model,"forearm_right")],new float[]{-7.35f,11.65f,-2.35f},new float[]{-3.65f,18,2.35f});
                if(overlaps(head,hand))throw new AssertionError("Forearm intersects head at "+phase+" "+t);
                var grip=mat[bone(model,"cigarette_grip")].transformPosition(new Vector3f(-4.3f,12.25f,-1.8f));
                var palm=mat[bone(model,"forearm_right")].transformPosition(new Vector3f(-4.3f,12.25f,-1.8f));
                if(grip.distance(palm)>.0001)throw new AssertionError("Cigarette detaches from grip");
                if(previous!=null)maxStep=Math.max(maxStep,previous.distance(grip));previous=new Vector3f(grip);
                for(String side:List.of("left","right")){
                    int foot=bone(model,"foot_"+side);float bottom=999;
                    for(var q:model.quads())if(q.bone()==foot)for(var v:q.vertices())bottom=Math.min(bottom,mat[foot].transformPosition(new Vector3f(v[0],v[1],v[2])).y);
                    minSole=Math.min(minSole,bottom);maxSole=Math.max(maxSole,bottom);
                    if(Math.abs(bottom+.35)>.045)throw new AssertionError("Foot leaves floor: "+phase+" "+t+" "+bottom);
                }
                if(phase.equals("play")){
                    for(String side:List.of("left","right")){
                        var local=new Matrix4f(mat[bone(model,"head")]).invert().mul(mat[bone(model,"lid_"+side)]);
                        float lid=local.getScale(new Vector3f()).y;
                        if(t>=3.1&&t<=3.8&&lid<.99f)throw new AssertionError("Inhale eyes are not closed");
                        if((t<2.8||t>4.15)&&lid>.002f)throw new AssertionError("Eyes remain closed outside inhale");
                    }
                }
                if(phase.equals("play")&&t>=3.1&&t<=3.8){
                    var filter=mat[bone(model,"cigarette_grip")].transformPosition(new Vector3f(-5.4f,12.25f,-1.8f));
                    var mouth=mat[bone(model,"head")].transformPosition(new Vector3f(-1.2f,24.8f,-4.3f));
                    if(filter.distance(mouth)>.5)throw new AssertionError("Cigarette fails to reach face "+filter.distance(mouth));
                }
            }
            if(model.clips().get(clip).loop()){
                var first=frame(model,pose,clip,0);pose.reset();pose.apply(clip,length-.00001);var near=pose.matrices();
                @SuppressWarnings("unchecked") var matrices=(List<float[]>)first.get("matrices");
                for(int i=0;i<near.length;i++)for(int j=0;j<16;j++)if(Math.abs(matrices.get(i)[j]-near[i].get(new float[16])[j])>.0002)throw new AssertionError("Loop seam");
            }
            for(double t:new double[]{.0131,.2371,.4781,.7131,.9431})parity.add(frame(model,pose,clip,t));
            if(length>1)for(double t:new double[]{2.7131,3.4713,4.2713,4.8131})parity.add(frame(model,pose,clip,t));
        }
        // Joining clips must preserve the full pose, including the prop and foot anchors.
        for(String[] pair:List.of(new String[]{"enter","play"},new String[]{"play","exit"},new String[]{"hold","exit"})){
            String from=prefix+pair[0],to=prefix+pair[1];
            pose.reset();pose.apply(from,model.clips().get(from).length());
            var end=Arrays.stream(pose.matrices()).map(Matrix4f::new).toList();
            pose.reset();pose.apply(to,0);var start=pose.matrices();
            for(int i=0;i<start.length;i++)if(!start[i].equals(end.get(i),.0001f))throw new AssertionError("Clip transition jumps: "+from+" -> "+to);
        }
        for(int i=0;i<180;i++){
            double t=i/25.0;String clip=t<1?prefix+"enter":t<6.2?prefix+"play":prefix+"exit";double local=t<1?t:t<6.2?t-1:t-6.2;
            frames.add(frame(model,pose,clip,local));
        }
        var report=Map.of("samples",samples,"minSole",minSole,"maxSole",maxSole,"maxGripStepAt120Hz",maxStep,"headClearance",true,"gripRigid",true,"loopSeams",true,"clipTransitions",true,"inhaleEyeClosure",true);
        Files.writeString(Path.of(args[1]),gson.toJson(Map.of("frames",frames,"parity",parity,"report",report)));
        System.out.println(gson.toJson(report));
    }
}
