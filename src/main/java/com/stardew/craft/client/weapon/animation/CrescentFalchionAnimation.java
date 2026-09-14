package com.stardew.craft.client.weapon.animation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.util.Mth;
import org.joml.Vector3f;
import static com.stardew.craft.client.weapon.MeleeWeaponVisuals.*;
import static com.stardew.craft.client.weapon.animation.WeaponSkillKeyframeTimeline.Easing.*;
public final class CrescentFalchionAnimation implements WeaponSkillAnimation {
    private static final WeaponSkillPose REST=new WeaponSkillPose(0,-90,25,1.13f,3.2f,1.13f);
    private static final Vector3f GRIP=new Vector3f(0,-.375f,0);
    private static final WeaponSkillKeyframeTimeline CUT=cut(true,false),CRESCENT=cut(true,true),STEEL=cut(false,false),LINE=cut(false,true);
    private static final WeaponSkillKeyframeTimeline TRACE=new WeaponSkillKeyframeTimeline(8,REST,GRIP,k(0,REST,LINEAR),k(2,new WeaponSkillPose(-22,-85,18,1.5f,3.6f,-.9f),SMOOTH),k(8,REST,SMOOTH));
    private final String id;
    public CrescentFalchionAnimation(String id){this.id=id;}
    public static boolean supports(String id){return CUTLASS_SWING.equals(id)||CRESCENT_SLASH.equals(id)||FALCHION_SWING.equals(id)||FALCHION_LINE.equals(id)||FALCHION_TRACE.equals(id);}
    private static WeaponSkillKeyframeTimeline timeline(String id){return switch(id){case CUTLASS_SWING->CUT;case CRESCENT_SLASH->CRESCENT;case FALCHION_SWING->STEEL;case FALCHION_LINE->LINE;case FALCHION_TRACE->TRACE;default->throw new IllegalArgumentException(id);};}
    static WeaponSkillPose sample(String id,float t){return timeline(id).sampleRight(t);}
    @Override public boolean apply(PoseStack stack,HumanoidArm arm,float t){return timeline(id).apply(stack,arm,t);}
    private static WeaponSkillKeyframeTimeline cut(boolean curved,boolean skill){
        float contact=curved&&skill?3:1.48f,load=curved&&skill?1.75f:.5f;
        return new WeaponSkillKeyframeTimeline(8,REST,GRIP,k(0,REST,LINEAR),
                k(load,new WeaponSkillPose(-25,-66,10,curved?3.05f:2.1f,3.8f,.2f),EASE_OUT_CUBIC),
                k(contact,new WeaponSkillPose(-34,-79,48,curved?-2.6f:-1.4f,curved?2.9f:2.5f,skill?-4.2f:-3.2f),EASE_IN_QUAD),
                k(curved&&skill?4.9f:3.2f,new WeaponSkillPose(-20,-86,37,curved?-1.3f:-.2f,3.1f,-1.1f),EASE_OUT_CUBIC),k(8,REST,SMOOTH));
    }
    static void applyBodyPose(HumanoidModel<?> model,LivingEntity entity,String id,float t){
        float contact=CRESCENT_SLASH.equals(id)?.375f:.185f;
        float sign=entity.getMainArm()==HumanoidArm.RIGHT?1:-1,w=Mth.clamp(t/.1f,0,1)*(1-Mth.clamp((t-.55f)/.45f,0,1));
        var arm=sign>0?model.rightArm:model.leftArm;arm.xRot=Mth.lerp(w,arm.xRot,-1.25f);
        arm.yRot=Mth.lerp(w,arm.yRot,sign*Mth.lerp(Mth.clamp(t/contact,0,1),-.4f,.25f));arm.zRot=Mth.lerp(w,arm.zRot,sign*.15f);
        model.body.yRot+=w*sign*(2*Mth.clamp(t/contact,0,1)-1)*.16f;
    }
    private static WeaponSkillKeyframeTimeline.Keyframe k(float t,WeaponSkillPose p,WeaponSkillKeyframeTimeline.Easing e){return new WeaponSkillKeyframeTimeline.Keyframe(t,p,e);}
}
