package com.stardew.craft.client.fishing;

import com.stardew.craft.client.npcnative.NativeNpcModel;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/** Local-channel sampling, native ZYX hierarchy, and normalized linear blend skinning. */
public final class FishingRigPose {
    public final FishingRigAssets.Rig rig;
    private final Map<String,Integer> names=new HashMap<>();
    final float[][] channels;
    public final Matrix4f[] world,skin;
    private final Matrix4f[] inverse;
    public FishingRigPose(FishingRigAssets.Rig rig) {
        this.rig=rig;int size=rig.bones().size();channels=new float[size][9];world=new Matrix4f[size];skin=new Matrix4f[size];inverse=new Matrix4f[size];
        for(int i=0;i<size;i++){names.put(rig.bones().get(i).name(),i);world[i]=new Matrix4f();skin[i]=new Matrix4f();inverse[i]=new Matrix4f().set(rig.bones().get(i).inverse());}reset();
    }
    public void reset(){for(float[] c:channels){Arrays.fill(c,0);c[6]=c[7]=c[8]=1;}}
    public int index(String name){return names.getOrDefault(name,-1);}
    public void sample(String name,double time){reset();var clip=FishingRigAssets.clips.get(name);if(clip!=null)apply(clip,time);matrices();}
    public void apply(NativeNpcModel.Clip clip,double time) {
        double t=clip.loop()?Math.floorMod((long)(time*1e9),(long)(clip.length()*1e9))/1e9:Math.max(0,Math.min(clip.length(),time));
        for(var track:clip.tracks()) {
            var keys=track.keys();int off=switch(track.channel()){case "rotation"->3;case "scale"->6;default->0;};
            int lo=0,hi=keys.size()-1;while(lo<hi){int mid=(lo+hi+1)/2;if(keys.get(mid).time()<=t)lo=mid;else hi=mid-1;}
            var a=keys.get(lo);var b=keys.get(Math.min(lo+1,keys.size()-1));
            float q=a==b||a.step()?0:(float)Math.max(0,Math.min(1,(t-a.time())/(b.time()-a.time())));
            for(int axis=0;axis<3;axis++)channels[track.bone()][off+axis]=t<a.time()?a.before()[axis]:a.after()[axis]+(b.before()[axis]-a.after()[axis])*q;
        }
    }
    public void copy(FishingRigPose other){for(int i=0;i<channels.length;i++)System.arraycopy(other.channels[i],0,channels[i],0,9);}
    public void mix(FishingRigPose from,float weight) {
        for(int i=0;i<channels.length;i++)for(int j=0;j<9;j++) {
            float delta=channels[i][j]-from.channels[i][j];if(j>=3&&j<6)delta=(delta%360+540)%360-180;
            channels[i][j]=from.channels[i][j]+delta*weight;
        }
        matrices();
    }
    public void matrices() {
        for(int i=0;i<world.length;i++){
            var b=rig.bones().get(i);float[] c=channels[i];var m=world[i];if(b.parent()<0)m.identity();else m.set(world[b.parent()]);
            m.translate(b.position()[0]+c[0],b.position()[1]+c[1],b.position()[2]+c[2]);
            m.rotateZYX((b.rotation()[2]+c[5])*(float)Math.PI/180,(b.rotation()[1]+c[4])*(float)Math.PI/180,(b.rotation()[0]+c[3])*(float)Math.PI/180);
            m.scale(c[6],c[7],c[8]);skin[i].set(m).mul(inverse[i]);
        }
    }
    /** Aim around the actual shoulder line, keeping both shoulder sockets fixed in the torso. */
    public void aim(float pitch) {aim(pitch,true);}
    public void aim(float pitch,boolean hangingTackle) {
        if(Math.abs(pitch)<.0001f)return;
        var pivot=new Matrix4f(world[index("torso")]).translate(0,11,0);
        var aim=new Matrix4f(pivot).rotateX((float)Math.toRadians(-pitch)).mul(new Matrix4f(pivot).invert());
        for(int i=0;i<world.length;i++){
            boolean owned=i>=index("rod_rod")&&(hangingTackle||i<index("rod_stowed_tackle")||i>=index("caught_fish"));
            for(int parent=i;!owned&&parent>=0;parent=rig.bones().get(parent).parent())owned=parent==index("arm_right")||parent==index("arm_left");
            if(owned){world[i].set(new Matrix4f(aim).mul(world[i]));skin[i].set(world[i]).mul(inverse[i]);}
        }
    }
    public Vector3f anchor(String name){int i=index(name);return i<0?new Vector3f():world[i].getTranslation(new Vector3f());}
    public Vector3f vertex(FishingRigAssets.Vertex v,Vector3f target) { return skinVertex(v,target,0,1,0); }
    public Vector3f skinVertex(FishingRigAssets.Vertex v,Vector3f target,float inflate,float width,float palmShift) {
        var rest=new Vector3f(v.point());float shoulder=rest.x<0?-6:6;
        float localX=rest.x-shoulder;
        rest.x=shoulder+localX*width+palmShift*Math.max(0,Math.min(1,(23-rest.y)/9)) + Math.signum(localX)*inflate;
        rest.z+=Math.signum(rest.z)*inflate;
        if(rest.y>=23)rest.y+=inflate;else if(rest.y<=11)rest.y-=inflate;
        target.zero();var p=new Vector3f();for(int i=0;i<v.bones().length;i++){p.set(rest);skin[v.bones()[i]].transformPosition(p);target.fma(v.weights()[i],p);}
        float inset=0;
        if(width<1) {
            // Narrow the shoulder into the torso, fading to zero at the authored palm/contact region.
            // Use the torso's axis so bending the arm cannot rotate this attachment offset away.
            inset=-Math.signum(shoulder)*2*(1-width)*Math.max(0,Math.min(1,(v.point()[1]-14)/9));
            target.add(world[index("torso")].transformDirection(p.set(inset,0,0)));
        }
        // The shoulder surface belongs to the torso; fixing only its bone pivot still lets
        // the entire cap rotate away from the body. Blend into the authored upper arm before
        // the first elbow interval, leaving elbow deformation and palm contacts unchanged.
        if(v.point()[1]>19.5f&&(v.bones()[0]==index("right_upper_arm")||v.bones()[0]==index("left_upper_arm"))) {
            float q=Math.min(1,(v.point()[1]-19.5f)/3.5f);q=q*q*(3-2*q);
            p.set(rest).add(inset,0,0);skin[index("torso")].transformPosition(p);
            target.lerp(p,q);
        }
        return target;
    }
}
