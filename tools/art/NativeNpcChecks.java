import com.google.gson.Gson;
import com.stardew.craft.npc.animation.SamActivity;
import com.stardew.craft.client.npcnative.IdleBlinkClock;
import com.stardew.craft.client.npcnative.NativeNpcModel;
import com.stardew.craft.client.npcnative.NativeNpcPose;
import com.stardew.craft.client.npcnative.NativeSamAttentionPose;
import com.stardew.craft.client.npcnative.NativeWalkClock;
import com.stardew.craft.npc.attention.NpcAttentionMotion;
import com.stardew.craft.npc.attention.NpcDialogueMotion;
import com.stardew.craft.npc.attention.NpcStareTracker;
import org.joml.Vector3f;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Headless executable checks; intentionally outside the project's local-only src/test. */
public final class NativeNpcChecks {
    static void near(double a, double b, double epsilon, String label) {
        if (Math.abs(a-b) > epsilon) throw new AssertionError(label + ": " + a + " != " + b);
    }
    public static void main(String[] args) throws Exception {
        var gson = new Gson();
        var model = gson.fromJson(Files.readString(Path.of(args[0])), NativeNpcModel.class);
        var pose = new NativeNpcPose(model);
        var before = new float[]{0,0,0};
        var after = new float[]{2,4,6};
        var keys = List.of(new NativeNpcModel.Key(0,before,before,false),
                new NativeNpcModel.Key(1,after,new float[]{3,5,7},true),
                new NativeNpcModel.Key(2,after,after,false));
        var track = new NativeNpcModel.Track(0,"position",keys);
        float[] out = new float[3];
        NativeNpcPose.sample(track,.5,out); near(out[2],3,1e-6,"linear");
        NativeNpcPose.sample(track,1,out); near(out[2],7,1e-6,"post at boundary");
        NativeNpcPose.sample(track,1.5,out); near(out[2],7,1e-6,"step hold");
        NativeNpcPose.sample(track,2,out); near(out[2],6,1e-6,"end");
        int head = -1, leg = -1, chest = -1;
        for (int i=0;i<model.bones().size();i++) {
            switch (model.bones().get(i).name()) {
                case "head" -> head=i;
                case "leg_left" -> leg=i;
                case "chest_breath" -> chest=i;
            }
        }
        if (Math.min(head,Math.min(leg,chest))<0) throw new AssertionError("Missing rig");
        pose.apply("animation.sam.idle",1.5);
        var matrices=pose.matrices();
        near(matrices[head].getScale(new Vector3f()).y,1,1e-6,"head retains size");
        near(matrices[chest].getScale(new Vector3f()).y,1.030,1e-6,"chest expansion");
        var foot = matrices[leg].transformPosition(new Vector3f(2,0,-3));
        var handBone = java.util.stream.IntStream.range(0,model.bones().size())
                .filter(i -> model.bones().get(i).name().equals("arm_left")).findFirst().orElseThrow();
        Vector3f firstHand = null;
        float handTravel = 0;
        for (int frame=0;frame<=240;frame++) {
            pose.reset();pose.apply("animation.sam.idle",frame/30.0);
            var current=pose.matrices();
            near(current[leg].transformPosition(new Vector3f(2,0,-3)).distance(foot),0,1e-6,"stationary sole");
            near(current[head].getScale(new Vector3f()).y,1,1e-6,"face retains size across cycle");
            var hand=current[handBone].transformPosition(new Vector3f(5.5f,12,0));
            if(firstHand==null)firstHand=new Vector3f(hand);
            handTravel=Math.max(handTravel,hand.distance(firstHand));
        }
        if(handTravel<.70f)throw new AssertionError("Breath follow-through not readable: "+handTravel);
        System.out.println("PASS: idle hands follow breath; travel="+handTravel+" model units; feet fixed, face unscaled");
        pose.reset();pose.apply("animation.sam.idle",0);
        var beginning=java.util.Arrays.stream(pose.matrices()).map(org.joml.Matrix4f::new).toList();
        pose.reset();pose.apply("animation.sam.idle",8);
        for(int i=0;i<beginning.size();i++) {
            float[] x=beginning.get(i).get(new float[16]), y=pose.matrices()[i].get(new float[16]);
            for(int j=0;j<16;j++)near(x[j],y[j],1e-6,"all-bone loop seam");
        }
        var uuid=UUID.fromString("423de551-a9b4-4a97-9c6b-6bbfcfcdf361");
        var sparse=new IdleBlinkClock(uuid,model.profile());
        var dense=new IdleBlinkClock(uuid,model.profile());
        double prior=0;
        int events=0; boolean blinking=false;
        for(int i=0;i<1200;i++) {
            double t=i/10.0;
            for(double s=prior;s<t;s+=1/144.0)dense.sample(s);
            double a=sparse.sample(t), b=dense.sample(t);
            near(a,b,1e-9,"frame-rate independent schedule");
            if(a>=0&&!blinking)events++;
            blinking=a>=0;prior=t;
        }
        if(events<10 || events>50)throw new AssertionError("Unexpected event count "+events);
        var different=new IdleBlinkClock(UUID.fromString("deadbeef-a9b4-4a97-9c6b-6bbfcfcdf362"),model.profile());
        var original=new IdleBlinkClock(uuid,model.profile());
        boolean differentPhase=false;
        for(int i=0;i<900;i++)if(different.sample(i/30.0)!=original.sample(i/30.0))differentPhase=true;
        if(!differentPhase)throw new AssertionError("Synchronized NPCs");
        var restart=new IdleBlinkClock(uuid,model.profile());
        sparse.sample(0);near(sparse.sample(2),restart.sample(2),1e-9,"clock rewind");
        // A hierarchy with a rotated parent: the child must rotate around its own rest pivot, then inherit.
        var fixture=new NativeNpcModel(1,"",List.of(
            new NativeNpcModel.Bone("parent",-1,new float[]{0,0,0},new float[]{0,0,90}),
            new NativeNpcModel.Bone("child",0,new float[]{2,0,0},new float[]{0,0,90})),List.of(),Map.of(),model.profile());
        var v=new NativeNpcPose(fixture).matrices()[1].transformPosition(new Vector3f(3,0,0));
        near(v.x,-1,1e-5,"parent-child x");near(v.y,2,1e-5,"parent-child y");
        checkActivities(Path.of(args[0]).getParent(), gson);
        checkAttention(model);
        checkWalk(model);
        checkDialogue(model);
        checkGuitar(gson.fromJson(Files.readString(Path.of(args[0]).resolveSibling("sam_guitar.json")),NativeNpcModel.class),
                args.length>1 ? Path.of(args[1]).resolveSibling("guitar-poses.json") : null,gson);
        if(args.length>1) {
            var clock=new IdleBlinkClock(uuid,model.profile());
            List<Map<String,Object>> frames=new ArrayList<>();
            for(int i=0;i<960;i++) {
                double time=i/30.0;
                double blink=clock.sample(time);
                pose.reset();pose.apply("animation.sam.idle",time+clock.idleOffset());
                if(blink>=0)pose.apply("animation.sam.blink",blink);
                List<float[]> transforms=new ArrayList<>();
                for(var matrix:pose.matrices())transforms.add(matrix.get(new float[16]));
                frames.add(Map.of("time",time,"blink",blink,"breath",time+clock.idleOffset(),"matrices",transforms));
            }
            Path output=Path.of(args[1]);Files.createDirectories(output.getParent());
            Files.writeString(output,gson.toJson(Map.of("fps",30,"frames",frames)));
            exportAttention(model,output.resolveSibling("attention-poses.json"),gson);
            exportDialogue(model,output.resolveSibling("dialogue-poses.json"),gson);
            exportWalk(model,output.resolveSibling("walk-poses.json"),gson);
        }
        System.out.println("PASS: interpolation, pre/post, step, hierarchy, breath isolation/loop, UUID phases, frame-rate independence, rewind; "+events+" blink events in 120s");
    }

    static void checkActivities(Path directory, Gson gson) throws Exception {
        if (SamActivity.fromAnimation(null)!=null || SamActivity.fromAnimation("idle")!=null)
            throw new AssertionError("Unrelated animation selected a prop");
        for (var action:SamActivity.values()) {
            for (var alias:List.of(action.asset(),action.playClip(),action.holdClip()))
                if (SamActivity.fromAnimation(alias)!=action) throw new AssertionError("Activity alias: "+alias);
            var model=gson.fromJson(Files.readString(directory.resolve(action.asset()+".json")),NativeNpcModel.class);
            near(model.profile().groundOffset(),action==SamActivity.SKATEBOARD?0:.12,1e-6,"activity ground offset");
            var pose=new NativeNpcPose(model);
            for (var clip:List.of(action.playClip(),action.holdClip())) {
                double duration=model.clips().get(clip).length();
                pose.reset();pose.apply(clip,0);var first=transforms(pose);
                // Sample immediately before wrapping; sampling exactly at duration hides broken seams.
                pose.reset();pose.apply(clip,Math.nextDown(duration));var last=transforms(pose);
                for(int b=0;b<first.size();b++)for(int j=0;j<16;j++)
                    near(first.get(b)[j],last.get(b)[j],.001,"activity pre-wrap seam "+clip);
                for(int frame=0;frame<=480;frame++) {
                    pose.reset();pose.apply(clip,frame*duration/480);
                    for(var matrix:pose.matrices())for(float value:matrix.get(new float[16]))
                        if(!Float.isFinite(value))throw new AssertionError("Invalid activity pose "+clip);
                }
            }
        }
        System.out.println("PASS: all three shipped Sam activities, aliases, ground offsets, finite poses and pre-wrap seams");
    }

    static void checkGuitar(NativeNpcModel model,Path output,Gson gson) throws Exception {
        var pose=new NativeNpcPose(model);
        int elbow=bone(model,"forearm_left"), shoulder=bone(model,"arm_left");
        var feet=Set.of(bone(model,"foot_left"),bone(model,"foot_right"));
        float min=Float.POSITIVE_INFINITY,max=Float.NEGATIVE_INFINITY;
        List<Map<String,Object>> frames=new ArrayList<>();
        for(int i=0;i<=400;i++) {
            double t=i/100.0;
            pose.reset();pose.apply("animation.sam.guitar_play",t);
            var m=pose.matrices();
            var e=m[elbow].transformPosition(new Vector3f(model.bones().get(elbow).origin()));
            var s=m[shoulder].transformPosition(new Vector3f(model.bones().get(shoulder).origin()));
            if(s.y-e.y<3)throw new AssertionError("Picking elbow rose toward shoulder");
            for(int foot:feet) {
                float lowest=Float.POSITIVE_INFINITY;
                for(var q:model.quads())if(q.bone()==foot)for(var v:q.vertices())
                    lowest=Math.min(lowest,m[foot].transformPosition(new Vector3f(v[0],v[1],v[2])).y);
                near(lowest,-model.profile().groundOffset(),.001,"guitar sole contact");
                min=Math.min(min,lowest);max=Math.max(max,lowest);
            }
            if(output!=null && i%4==0 && i<400)frames.add(Map.of("time",t,"matrices",transforms(pose)));
        }
        if(max-min>.001)throw new AssertionError("Guitar stance floats");
        pose.reset();pose.apply("animation.sam.guitar_play",0);var first=transforms(pose);
        pose.reset();pose.apply("animation.sam.guitar_play",4);var last=transforms(pose);
        for(int i=0;i<first.size();i++)for(int j=0;j<16;j++)near(first.get(i)[j],last.get(i)[j],1e-6,"guitar seam");
        pose.reset();pose.apply("animation.sam.guitar_hold",.5);
        for(String name:List.of("music_note_1","music_note_2","music_note_3"))
            near(pose.matrices()[bone(model,name)].getScale(new Vector3f()).x,.001,1e-6,"notes hidden in hold");
        if(output!=null){Files.createDirectories(output.getParent());Files.writeString(output,gson.toJson(Map.of("fps",25,"frames",frames)));}
        System.out.println("PASS: production guitar, low elbow, stationary soles, loop seam and hidden hold notes");
    }

    static void checkAttention(NativeNpcModel model) {
        var tracker=new NpcStareTracker();
        var a=new NpcStareTracker.Candidate(1,9);
        var b=new NpcStareTracker.Candidate(2,4);
        for(int i=0;i<49;i++)near(tracker.tick(i,List.of(a,b),true),-1,0,"no early stare trigger");
        near(tracker.tick(49,List.of(a,b),true),2,0,"one shared nearest tie winner");
        tracker.coolDownUntil(300);
        for(int i=50;i<300;i++)near(tracker.tick(i,List.of(a),true),-1,0,"cooldown");
        for(int i=300;i<340;i++)tracker.tick(i,List.of(a),true);
        tracker.tick(340,List.of(),true); // Ray misses / obstruction reset dwell, not pause it.
        for(int i=341;i<390;i++)near(tracker.tick(i,List.of(a),true),-1,0,"continuous dwell reset");
        near(tracker.tick(390,List.of(a),true),1,0,"reacquire after reset");
        tracker.tick(391,List.of(a),false);
        for(int i=392;i<441;i++)near(tracker.tick(i,List.of(a),true),-1,0,"busy resets dwell");
        var first=NpcAttentionMotion.sample(.20,130,10);
        if(first.headYaw()<=0 || first.bodyYaw()!=0)
            throw new AssertionError("Head must lead the body turn");
        int right=bone(model,"leg_right"),left=bone(model,"leg_left");
        var pose=new NativeNpcPose(model);
        for(double yaw:new double[]{-180,-130,-50,-35,-15,0,15,35,50,130,180}) {
            Vector3f[] planted=new Vector3f[2];

            for(int i=0;i<=Math.ceil(NpcAttentionMotion.duration(yaw)*120);i++) {
                double time=i/120.0;
                var s=NpcAttentionMotion.sample(time,yaw,30);
                if(Math.abs(s.headYaw())>48.0001 || Math.abs(s.headPitch())>18.0001)
                    throw new AssertionError("Head limits");

                pose.reset();pose.apply("animation.sam.idle",time);
                NativeSamAttentionPose.apply(pose,s,1);
                var matrices=pose.matrices();
                for(String name:List.of("iris_right","iris_left")) {
                    float[] eye=matrices[bone(model,name)].get(new float[16]);
                    float[] h=matrices[bone(model,"head")].get(new float[16]);
                    for(int k=0;k<16;k++)near(eye[k],h[k],1e-5,"iris fixed relative to head");
                }
                for(int side=0;side<2;side++) {
                    var f=side==0?s.right():s.left();
                    int index=side==0?right:left;
                    var center=matrices[index].transformPosition(new Vector3f(side==0?-2:2,0,-3));
                    // Runtime matrices use floats; 0.0001 authoring units is 1/160000 block.
                    near(center.x,f.toeX(),1e-4,"toe contact world x");
                    near(center.z,f.toeZ(),1e-4,"toe contact world z");
                    near(center.y,f.lift(),1e-4,"foot sole height");
                    if(f.lift()<1e-9) {
                        if(planted[side]!=null) {
                            near(center.distance(planted[side]),0,1e-4,"planted foot does not slide");
                        }
                        planted[side]=new Vector3f(center);
                    } else planted[side]=null;
                }
            }
            int liftEvents=0;
            boolean[] airborne={false,false};
            for(int frame=0;frame<=Math.ceil((.3+NpcAttentionMotion.turnTime(yaw))*120);frame++) {
                var s=NpcAttentionMotion.sample(frame/120.0,yaw,0);
                double[] heights={s.right().lift(),s.left().lift()};
                for(int side=0;side<2;side++) {
                    boolean air=heights[side]>.00001;
                    if(air&&!airborne[side])liftEvents++;
                    airborne[side]=air;
                }
            }
            near(liftEvents,NpcAttentionMotion.steps(yaw),0,"one turning step, two only near rear");
            var end=NpcAttentionMotion.sample(NpcAttentionMotion.duration(yaw),yaw,10);
            near(end.bodyYaw(),0,1e-6,"return body");near(end.headYaw(),0,1e-6,"return head");
            near(end.rootX(),0,1e-6,"return body position x");
            near(end.rootZ(),0,1e-6,"return body position z");
            for(double t:new double[]{0,.08,.3,1,2}) {
                pose.reset();pose.apply("animation.sam.idle",t);
                var base=Arrays.stream(pose.matrices()).map(org.joml.Matrix4f::new).toList();
                NativeSamAttentionPose.apply(pose,NpcAttentionMotion.sample(1,yaw,10),0);
                for(int j=0;j<base.size();j++) {
                    float[] x=base.get(j).get(new float[16]),y=pose.matrices()[j].get(new float[16]);
                    for(int k=0;k<16;k++)near(x[k],y[k],1e-5,"cancel reaches original pose");
                }
            }
        }
        System.out.println("PASS: stare dwell/reset/selection/cooldown, head leads, fixed irises, head limits, 11 turn angles with anchored toes, one/two steps, return/cancel");
    }

    static void checkDialogue(NativeNpcModel model) {
        var pose=new NativeNpcPose(model);
        for(double yaw:new double[]{-175,-130,-90,-25,0,25,90,130,175}) {
            double ready=NpcDialogueMotion.readyTime(yaw);
            var held=NpcAttentionMotion.sample(ready,yaw,8);
            // A long multiplayer conversation must not run the return on a short timer.
            for(double age:new double[]{ready,5,30,120}) {
                double t=NpcDialogueMotion.motionTime(age,yaw,-1);
                near(t,ready,1e-9,"dialogue holds until close");
                if(NpcDialogueMotion.finished(age,yaw,-1))throw new AssertionError("Open dialogue ended");
            }
            double release=30;
            var atClose=NpcAttentionMotion.sample(NpcDialogueMotion.motionTime(release,yaw,release),yaw,8);
            near(atClose.bodyYaw(),held.bodyYaw(),1e-9,"close preserves body heading");
            near(atClose.headYaw(),held.headYaw(),1e-9,"close preserves head heading");
            near(atClose.rootX(),held.rootX(),1e-9,"close preserves pivot offset");
            for(int i=0;i<=300;i++) {
                double age=release+i/120.0;
                var s=NpcAttentionMotion.sample(NpcDialogueMotion.motionTime(age,yaw,release),yaw,8);
                pose.reset();pose.apply("animation.sam.idle",age);NativeSamAttentionPose.apply(pose,s,1);
                pose.groundFeet(model.profile().groundOffset());
                checkSoles(model,pose);
            }
            double end=release+NpcAttentionMotion.duration(yaw)-NpcAttentionMotion.holdEnd(yaw)+.001;
            var returned=NpcAttentionMotion.sample(NpcDialogueMotion.motionTime(end,yaw,release),yaw,8);
            near(returned.bodyYaw(),0,1e-9,"dialogue restores original heading");
            near(returned.rootX(),0,1e-9,"dialogue restores original pivot position");
            near(returned.rootZ(),0,1e-9,"dialogue restores original pivot position");
            if(!NpcDialogueMotion.finished(end,yaw,release))throw new AssertionError("Dialogue return never ends");
            near(NpcDialogueMotion.motionTime(.2,yaw,.1),.2,1e-9,"early disconnect finishes outgoing support first");
            // The existing glance can be promoted at any point before its return with no pose jump.
            for(double age:new double[]{.2,.6,1,ready, NpcAttentionMotion.holdEnd(yaw)}) {
                var from=NpcAttentionMotion.sample(age,yaw,8);
                var promoted=NpcAttentionMotion.sample(NpcDialogueMotion.motionTime(age,yaw,-1),yaw,8);
                near(from.bodyYaw(),promoted.bodyYaw(),1e-9,"stare promotion preserves body");
                near(from.headYaw(),promoted.headYaw(),1e-9,"stare promotion preserves head");
            }
        }
        for(double yaw:new double[]{-170,-90,20,90,170}) {
            double hold=NpcAttentionMotion.holdEnd(yaw);
            for(double returning:new double[]{.1,.4,.8}) {
                double fromTime=Math.min(hold+returning,NpcAttentionMotion.duration(yaw)-.01);
                near(NpcDialogueMotion.motionTime(0,yaw,-1,fromTime),fromTime,1e-9,"click during return keeps exact pose");
                double ready=NpcDialogueMotion.readyTime(yaw,fromTime);
                near(NpcDialogueMotion.motionTime(ready,yaw,-1,fromTime),hold,1e-9,"return takeover faces player again");
                near(NpcDialogueMotion.motionTime(90,yaw,-1,fromTime),hold,1e-9,"return takeover holds for session");
                double last=fromTime;
                for(int i=1;i<=120;i++) {
                    double t=NpcDialogueMotion.motionTime(ready*i/120,yaw,-1,fromTime);
                    if(t>last+1e-9 || t<hold-1e-9)throw new AssertionError("Return takeover reverses direction twice");
                    last=t;
                    pose.reset();pose.apply("animation.sam.idle",i/30.0);
                    NativeSamAttentionPose.apply(pose,NpcAttentionMotion.sample(t,yaw,8),1);
                    pose.groundFeet(model.profile().groundOffset());checkSoles(model,pose);
                }
                near(NpcDialogueMotion.motionTime(90,yaw,90,fromTime),hold,1e-9,"resumed chat closes continuously");
            }
        }
        var from=NpcAttentionMotion.sample(.7,130,8);
        var to=NpcAttentionMotion.sample(0,-90,4);
        if(!NpcDialogueMotion.blend(from,to,0).equals(from) || !NpcDialogueMotion.blend(from,to,1).equals(to))
            throw new AssertionError("Takeover must preserve exact endpoints");
        System.out.println("PASS: dialogue long hold, close continuity, 9 return angles/soles, early disconnect, stare promotion and mid-return takeover");
    }

    static int bone(NativeNpcModel model,String name) {
        return java.util.stream.IntStream.range(0,model.bones().size())
                .filter(i->model.bones().get(i).name().equals(name)).findFirst().orElseThrow();
    }

    static void checkWalk(NativeNpcModel model) {
        var pose=new NativeNpcPose(model);
        double stride=model.profile().walkStride();
        if(stride<=.6 || stride>=1.0)throw new AssertionError("Level-walk stride outside measured range");
        float previousBodyY=Float.NaN,previousDelta=Float.NaN;
        double maxBodyDelta=0, minBody=Double.POSITIVE_INFINITY, maxBody=Double.NEGATIVE_INFINITY;
        double shoulderMin=99,shoulderMax=-99,kneeMin=180,kneeMax=-180;
        // Include two cycles: an equal first/last key alone does not detect a drop just before it.
        for(int i=0;i<=240;i++) {
            double t=i/120.0;
            pose.reset();pose.apply("animation.sam.idle",t);
            pose.apply("animation.sam.walk",t);pose.groundFeet(model.profile().groundOffset());
            float y=pose.matrices()[bone(model,"body")].transformPosition(new Vector3f(0,12,0)).y;
            minBody=Math.min(minBody,y);maxBody=Math.max(maxBody,y);
            double shoulderX=pose.boneMatrix("body").transformPosition(new Vector3f(0,24,0)).x;
            shoulderMin=Math.min(shoulderMin,shoulderX);shoulderMax=Math.max(shoulderMax,shoulderX);
            for(String side:List.of("right","left")) {
                int sign=side.equals("right")?-1:1;
                var root=pose.boneMatrix("root");
                var hip=pose.boneMatrix("leg_"+side).transformPosition(new Vector3f(sign*2,12,0));
                near(hip.distance(root.transformPosition(new Vector3f(sign*2,12,0))),0,.0001,"hip stays attached during weight transfer");
                var upper=pose.boneMatrix("leg_"+side);
                var knee=pose.boneMatrix("shin_"+side);
                var relative=new org.joml.Matrix4f(upper).invert().mul(knee);
                double bend=Math.toDegrees(Math.atan2(relative.m12(),relative.m22()));
                kneeMin=Math.min(kneeMin,bend);kneeMax=Math.max(kneeMax,bend);
                if(bend>0.001 || bend< -85)throw new AssertionError("Knee hyperextension or excessive folding");
                near(knee.getScale(new Vector3f()).y,1,.00001,"rigid shin length");
            }
            if(!Float.isNaN(previousBodyY)) {
                float delta=y-previousBodyY;
                maxBodyDelta=Math.max(maxBodyDelta,Math.abs(delta));
                if(Math.abs(delta)>.10)throw new AssertionError("Body jumps at contact/loop: t="+t+" delta="+delta);
                if(!Float.isNaN(previousDelta) && Math.abs(delta-previousDelta)>.025)
                    throw new AssertionError("Body velocity breaks at support change: "+t);
                previousDelta=delta;
            }
            previousBodyY=y;
        }
        if(maxBody-minBody<.45 || maxBody-minBody>.85)throw new AssertionError("Walking weight must remain readable without hopping: "+(maxBody-minBody));
        if(shoulderMax-shoulderMin<1.1 || shoulderMax-shoulderMin>1.6)
            throw new AssertionError("Shoulder weight transfer disappears or sways excessively");
        if(kneeMax-kneeMin<15 || kneeMin < -50 || kneeMax < -20)throw new AssertionError("Knee recovery is missing");
        for(int i=0;i<=720;i++) {
            double t=i/720.0;
            pose.reset();pose.apply("animation.sam.walk",t);
            var matrices=pose.matrices();
            for(int side=0;side<2;side++) {
                double p=(t+side*.5)%1;
                if(p>.55)continue;
                var m=matrices[bone(model,side==0?"foot_right":"foot_left")];
                float soleZ=p<.12?2.12F:-3.12F;
                var contact=m.transformPosition(new Vector3f(side==0?-2:2,-.12F,soleZ));
                double expected=soleZ+stride*16*(p-.275);
                near(contact.z,expected,.001,"grounded heel/toe follows authored distance without skating");
                near(contact.y,-.12,.002,"grounded support");
                near(contact.x,side==0?-2.12:2.12,.001,"support foot holds laterally while pelvis shifts");
            }
            pose.groundFeet(model.profile().groundOffset());
            checkSoles(model,pose);
            var head=pose.matrices()[bone(model,"head")];
            near(head.getScale(new Vector3f()).y,1,1e-5,"walking face is never scaled");
            for(String iris:List.of("iris_right","iris_left")) {
                float[] a=head.get(new float[16]), b=pose.matrices()[bone(model,iris)].get(new float[16]);
                for(int k=0;k<16;k++)near(a[k],b[k],1e-5,"walking keeps iris fixed");
            }
        }
        // Verify closure near the actual end, not simply modulo(time=length) back to the first key.
        for(var track:model.clips().get("animation.sam.walk").tracks()) {
            float[] a=new float[3],b=new float[3];
            NativeNpcPose.sample(track,0,a);NativeNpcPose.sample(track,1,b);
            for(int k=0;k<3;k++)near(a[k],b[k],1e-6,"walk authored seam");
        }
        for(int phase=0;phase<60;phase++) for(int w=0;w<=10;w++) {
            pose.reset();pose.apply("animation.sam.idle",1.7);
            pose.blend("animation.sam.walk",phase/60.0,w/10.0);
            pose.groundFeet(model.profile().groundOffset());checkSoles(model,pose);
        }
        double reference=Double.NaN;
        for(int fps:new int[]{15,30,60,144}) {
            var clock=new NativeWalkClock(stride);
            NativeWalkClock.Sample s=null;
            for(int i=0;i<=fps*4;i++)s=clock.sample(i/(double)fps,0,-1.6*i/fps,true);
            near(s.phase(),.3+6.4/stride,1e-8,"distance-driven phase");
            near(s.weight(),1,1e-8,"fully entered walk");
            if(Double.isNaN(reference))reference=s.phase();else near(s.phase(),reference,1e-8,"FPS independence");
            double frozen=s.phase();
            for(int i=1;i<=fps;i++)s=clock.sample(4+i/(double)fps,0,-6.4,true);
            near(s.phase(),frozen,1e-8,"stationary phase freezes");near(s.weight(),0,1e-8,"stop returns to idle");
            s=clock.sample(5.1,0,-40,true);near(s.weight(),0,0,"teleport resets, never spins feet");
            for(int i=1;i<=fps;i++)s=clock.sample(5.1+i/(double)fps,0,-40-i/(double)fps,false);
            near(s.weight(),0,0,"airborne cannot walk");
        }
        System.out.println("PASS: walking support contacts, inflated soles, 660 transition samples, authored seam, fixed eyes, distance/FPS/stop/teleport/airborne");
        System.out.println("PASS: body height/velocity across two cycles; max 1/120-cycle height change="+maxBodyDelta+"; body excursion="+(maxBody-minBody));
        System.out.println("PASS: attached hips, rigid shins, knee range="+kneeMin+".."+kneeMax+", shoulder lateral travel="+(shoulderMax-shoulderMin));
    }

    static void checkSoles(NativeNpcModel model,NativeNpcPose pose) {
        var matrices=pose.matrices();
        int right=bone(model,"leg_right"),left=bone(model,"leg_left");
        float lowest=Float.POSITIVE_INFINITY;
        for(var q:model.quads())if(pose.isLegBone(q.bone()))for(var v:q.vertices()) {
            float y=matrices[q.bone()].transformPosition(new Vector3f(v[0],v[1],v[2])).y+model.profile().groundOffset();
            lowest=Math.min(lowest,y);
        }
        if(lowest<-.00001)throw new AssertionError("Sole penetrates ground: "+lowest);
    }

    static void exportWalk(NativeNpcModel model,Path output,Gson gson) throws Exception {
        List<Map<String,Object>> frames=new ArrayList<>(),parity=new ArrayList<>();
        var pose=new NativeNpcPose(model);
        for(int i=0;i<120;i++) {
            double phase=i/119.0;
            pose.reset();pose.apply("animation.sam.walk",phase);
            parity.add(Map.of("time",phase,"walk",phase,"blink",-1,"breath",0,"matrices",transforms(pose)));
        }
        var clock=new NativeWalkClock(model.profile().walkStride());
        var blinkClock=new IdleBlinkClock(UUID.fromString("423de551-a9b4-4a97-9c6b-6bbfcfcdf361"),model.profile());
        double distance=0;
        for(int i=0;i<480;i++) {
            double time=i/30.0;
            double speed=time<1?0:time<1.4?1.35*(time-1)/.4:time<5?1.35:
                    time<5.4?1.35+.55*(time-5)/.4:time<9?1.9:time<9.4?1.9*(9.4-time)/.4:
                    time<11?0:time<11.4?1.1*(time-11)/.4:time<14?1.1:time<14.4?1.1*(14.4-time)/.4:0;
            distance+=speed/30;
            var walk=clock.sample(time,0,-distance,true);
            double blink=blinkClock.sample(time);
            pose.reset();pose.apply("animation.sam.idle",time+blinkClock.idleOffset());
            pose.blend("animation.sam.walk",walk.phase(),walk.weight());
            if(blink>=0)pose.apply("animation.sam.blink",blink);
            if(walk.weight()>0)pose.groundFeet(model.profile().groundOffset());
            frames.add(Map.of("time",time,"blink",blink,"breath",time+blinkClock.idleOffset(),
                    "walk",walk.phase(),"weight",walk.weight(),"distance",distance,"speed",speed,"matrices",transforms(pose)));
        }
        Files.writeString(output,gson.toJson(Map.of("fps",30,"procedural",true,"parityFrames",parity,"frames",frames)));
        List<Map<String,Object>> cycle=new ArrayList<>();
        for(int i=0;i<60;i++) {
            double phase=i/60.0;
            pose.reset();pose.apply("animation.sam.walk",phase);pose.groundFeet(model.profile().groundOffset());
            cycle.add(Map.of("time",phase,"walk",phase,"blink",-1,"breath",0,
                    "distance",phase*model.profile().walkStride(),"matrices",transforms(pose)));
        }
        Files.writeString(output.resolveSibling("walk-cycle-poses.json"),
                gson.toJson(Map.of("fps",60,"procedural",true,"parityFrames",parity,"frames",cycle)));
    }

    static List<float[]> transforms(NativeNpcPose pose) {
        List<float[]> result=new ArrayList<>();
        for(var matrix:pose.matrices())result.add(matrix.get(new float[16]));
        return result;
    }

    static void exportDialogue(NativeNpcModel model,Path output,Gson gson) throws Exception {
        var pose=new NativeNpcPose(model);
        var clock=new IdleBlinkClock(UUID.fromString("423de551-a9b4-4a97-9c6b-6bbfcfcdf361"),model.profile());
        List<Map<String,Object>> frames=new ArrayList<>();
        double yaw=130,release=4;
        for(int i=0;i<240;i++) {
            double time=i/30.0,age=time-.5;
            pose.reset();pose.apply("animation.sam.idle",time+clock.idleOffset());
            double blink=clock.sample(time);
            if(age>=0 && !NpcDialogueMotion.finished(age,yaw,release)) {
                double motion=NpcDialogueMotion.motionTime(age,yaw,release);
                var sample=NpcAttentionMotion.sample(motion,yaw,5);
                NativeSamAttentionPose.apply(pose,sample,1);
                if(motion<.55 || motion>NpcAttentionMotion.holdEnd(yaw)-.20)blink=sample.blink();
                pose.groundFeet(model.profile().groundOffset());
            }
            if(blink>=0)pose.apply("animation.sam.blink",blink);
            frames.add(Map.of("time",time,"blink",blink,"matrices",transforms(pose)));
        }
        Files.writeString(output,gson.toJson(Map.of("fps",30,"procedural",true,"frames",frames)));
    }

    static void exportAttention(NativeNpcModel model,Path output,Gson gson) throws Exception {
        List<Map<String,Object>> frames=new ArrayList<>();
        var pose=new NativeNpcPose(model);
        // Rest, small glance to the viewer's right, rest, then a large turn in the other direction.
        for(int i=0;i<510;i++) {
            double time=i/30.0, age=time<8?time-1:time-9;
            double yaw=time<8?-25:130;
            pose.reset();pose.apply("animation.sam.idle",time);
            double blink=-1;
            if(age>=0 && age<NpcAttentionMotion.duration(yaw)) {
                var s=NpcAttentionMotion.sample(age,yaw,5);
                NativeSamAttentionPose.apply(pose,s,1);blink=s.blink();
            }
            if(blink>=0)pose.apply("animation.sam.blink",blink);
            List<float[]> transforms=new ArrayList<>();
            for(var matrix:pose.matrices())transforms.add(matrix.get(new float[16]));
            frames.add(Map.of("time",time,"blink",blink,"breath",time,"matrices",transforms));
        }
        Files.writeString(output,gson.toJson(Map.of("fps",30,"procedural",true,"frames",frames)));
    }
}
