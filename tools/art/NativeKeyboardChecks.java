import com.google.gson.Gson;
import com.stardew.craft.client.npcnative.NativeNpcModel;
import com.stardew.craft.client.npcnative.NativeNpcPose;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import java.nio.file.*;
import java.util.*;

/** Production-pose checks for character/keyboard contacts and fixed furniture. */
public final class NativeKeyboardChecks {
    static int bone(NativeNpcModel m,String name){return NativeSmokingChecks.bone(m,name);}
    static Map<String,Object> frame(NativeNpcModel m,NativeNpcPose p,String clip,double t){return NativeSmokingChecks.frame(m,p,clip,t);}
    public static void main(String[] args)throws Exception{
        var gson=new Gson();var m=gson.fromJson(Files.readString(Path.of(args[0])),NativeNpcModel.class);var p=new NativeNpcPose(m);
        var npc=gson.fromJson(Files.readString(Path.of(args[2])),NativeNpcModel.class);
        var furniture=gson.fromJson(Files.readString(Path.of(args[3])),NativeNpcModel.class);
        var npcPose=new NativeNpcPose(npc);var furniturePose=new NativeNpcPose(furniture);
        var placement=new Matrix4f().translation(0,-.35f,-12).rotateY((float)Math.PI);var inversePlacement=new Matrix4f(placement).invert();
        var parts=new LinkedHashMap<String,float[][]>();var partBone=new HashMap<String,Integer>();
        for(var q:m.quads())if(q.sourcePart().startsWith("instrument_")){
            var bounds=parts.computeIfAbsent(q.sourcePart(),k->new float[][]{{999,999,999},{-999,-999,-999}});partBone.put(q.sourcePart(),q.bone());
            for(var v:q.vertices())for(int a=0;a<3;a++){bounds[0][a]=Math.min(bounds[0][a],v[a]);bounds[1][a]=Math.max(bounds[1][a],v[a]);}
        }
        int count=0;float minFoot=999,maxFoot=-999,maxSpeed=0,maxGripError=0;var parity=new ArrayList<Map<String,Object>>();String prefix="animation.sebastian.keyboard_";
        for(String phase:List.of("enter","play","exit")){
            String clip=prefix+phase;double len=m.clips().get(clip).length();Vector3f[] last={null,null};
            for(int j=0;j<=Math.round(len*120);j++){
                double t=Math.min(len,j/120.);p.reset();p.apply(clip,t);var matrices=p.matrices();count++;
                for(var matrix:matrices)if(!matrix.isFinite())throw new AssertionError("Invalid matrix");
                if(j%4==0){
                    npcPose.reset();npcPose.apply(clip,t);var nm=npcPose.matrices();
                    for(int k=0;k<npc.bones().size();k++)if(!nm[k].equals(matrices[bone(m,npc.bones().get(k).name())],.0001f))throw new AssertionError("NPC assembly differs from independent source");
                    furniturePose.reset();furniturePose.apply("animation.sebastian_keyboard."+phase,t);var fm=furniturePose.matrices();
                    for(int k=0;k<furniture.bones().size();k++){
                        var expected=new Matrix4f(placement).mul(fm[k]).mul(inversePlacement);
                        if(!expected.equals(matrices[bone(m,"instrument_"+furniture.bones().get(k).name())],.0001f))throw new AssertionError("Furniture assembly differs from independent source");
                    }
                }

                for(int i=0;i<m.bones().size();i++)if(m.bones().get(i).name().startsWith("instrument_")&&!m.bones().get(i).name().startsWith("instrument_white_")&&!matrices[i].equals(new Matrix4f(),.0001f))throw new AssertionError("Furniture moves "+m.bones().get(i).name());
                int si=0;
                for(String side:List.of("right","left")){
                    float sign=side.equals("right")?-1:1;int arm=bone(m,"forearm_"+side);float xlo=sign<0?-7:4,xhi=sign<0?-4:7;
                    var hand=NativeSmokingChecks.box(matrices[arm],new float[]{xlo,12,-2},new float[]{xhi,18,2});
                    for(var entry:parts.entrySet()){
                        String name=entry.getKey();var bounds=entry.getValue();
                        var object=NativeSmokingChecks.box(matrices[partBone.get(name)],bounds[0],bounds[1]);
                        if(NativeSmokingChecks.overlaps(hand,object))throw new AssertionError("Hand intersects "+name+" at "+phase+" "+t+" "+side);
                    }
                    var contact=matrices[arm].transformPosition(new Vector3f(5.5f*sign,12,2));
                    if(last[si]!=null)maxSpeed=Math.max(maxSpeed,last[si].distance(contact)*120);last[si++]=new Vector3f(contact);
                    if(phase.equals("play")){
                        double period=side.equals("right")?1:.5;double f=(t%period)/period;
                        if(f>=.20&&f<=.53){
                            int ki=Math.min(14,Math.max(1,(int)Math.round((contact.x+13)/2)+1));int kb=bone(m,String.format("instrument_white_%02d",ki));
                            var key=matrices[kb].transformPosition(new Vector3f(contact.x,16.15f,-8.04f));float error=Math.abs(contact.y-key.y-.018f);maxGripError=Math.max(maxGripError,error);
                            if(error>.045f)throw new AssertionError("Visible hand detaches from pressed key "+error);
                        }
                    }
                    int foot=bone(m,"foot_"+side);float bottom=999;
                    for(var q:m.quads())if(q.bone()==foot)for(var v:q.vertices())bottom=Math.min(bottom,matrices[foot].transformPosition(new Vector3f(v[0],v[1],v[2])).y);
                    minFoot=Math.min(minFoot,bottom);maxFoot=Math.max(maxFoot,bottom);if(Math.abs(bottom+.35)>.035)throw new AssertionError("Sole drift "+bottom);
                }
            }
            for(double t:new double[]{.0171,.4131,.8731,1.2731})parity.add(frame(m,p,clip,t));
            if(phase.equals("play"))for(double t:new double[]{1.9371,2.2731,3.4913,4.4713,5.1731,6.9131,7.7131})parity.add(frame(m,p,clip,t));
        }
        for(String[] pair:List.of(new String[]{"enter","play"},new String[]{"play","exit"},new String[]{"play","play"})){
            String a=prefix+pair[0],b=prefix+pair[1];p.reset();p.apply(a,m.clips().get(a).length()-1e-6);var end=Arrays.stream(p.matrices()).map(Matrix4f::new).toList();p.reset();p.apply(b,0);var start=p.matrices();
            for(int i=0;i<start.length;i++)if(!start[i].equals(end.get(i),.0003f))throw new AssertionError("Join discontinuity "+a+" -> "+b);
        }
        var frames=new ArrayList<Map<String,Object>>();for(int i=0;i<324;i++){
            double t=i/30.;String clip=t<1.4?prefix+"enter":t<9.4?prefix+"play":prefix+"exit";double local=t<1.4?t:t<9.4?t-1.4:t-9.4;frames.add(frame(m,p,clip,local));
        }
        var report=Map.of("samples",count,"minSole",minFoot,"maxSole",maxFoot,"maxHandSpeedUnitsPerSecond",maxSpeed,"maxPressedHandGapError",maxGripError,"furnitureFixed",true,"handClearance",true,"seams",true,"independentSourcesMatch",true);
        Files.writeString(Path.of(args[1]),gson.toJson(Map.of("frames",frames,"parity",parity,"report",report)));System.out.println(gson.toJson(report));
    }
}
