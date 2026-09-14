package com.stardew.craft.client.npcnative;

/** Keep a supported activity's approach transform while blending its standing body pose. */
public final class NativeActivityStandingPose {
    private NativeActivityStandingPose() {}

    public static void blend(NativeNpcPose pose,NativeNpcPose reference,NativeNpcModel model,
                             String idle,double idleTime,String entry,boolean supported,double weight) {
        if(weight<=0)return;
        if(!supported){pose.blend(idle,idleTime,weight);return;}
        reference.reset();reference.apply(idle,idleTime);
        if(supported) {
            var clip=model.clips().get(entry);
            if(clip!=null)for(var track:clip.tracks()) {
                if(!model.bones().get(track.bone()).name().equals("root"))continue;
                float[] value=new float[3];NativeNpcPose.sample(track,0,value);
                if(track.channel().equals("position"))reference.addPosition("root",value[0],value[1],value[2]);
                else if(track.channel().equals("rotation"))reference.addRotation("root",value[0],value[1],value[2]);
            }
        }
        pose.blendFrom(reference,weight);
    }
}
