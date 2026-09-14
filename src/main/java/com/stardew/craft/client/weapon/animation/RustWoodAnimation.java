package com.stardew.craft.client.weapon.animation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.util.Mth;
import org.joml.Vector3f;
import static com.stardew.craft.client.weapon.MeleeWeaponVisuals.*;
import static com.stardew.craft.client.weapon.animation.WeaponSkillKeyframeTimeline.Easing.*;
public final class RustWoodAnimation implements WeaponSkillAnimation {
    private static final WeaponSkillPose REST=new WeaponSkillPose(0,-90,25,1.13f,3.2f,1.13f);
    private static final Vector3f GRIP=new Vector3f(0,-.375f,0);
    private static final WeaponSkillKeyframeTimeline RUST=cut(false,false),TETANUS=cut(false,true),WOOD=cut(true,false),BLESS=cut(true,true);
    private final String id;
    public RustWoodAnimation(String id){this.id=id;}
    public static boolean supports(String id){return RUST_SWING.equals(id)||RUST_STRIKE.equals(id)||WOOD_SWING.equals(id)||WOOD_BLESS.equals(id);}
    private static WeaponSkillKeyframeTimeline timeline(String id){return switch(id){case RUST_SWING->RUST;case RUST_STRIKE->TETANUS;case WOOD_SWING->WOOD;case WOOD_BLESS->BLESS;default->throw new IllegalArgumentException(id);};}
    static WeaponSkillPose sample(String id,float t){return timeline(id).sampleRight(t);}
    @Override public boolean apply(PoseStack stack,HumanoidArm arm,float t){return timeline(id).apply(stack,arm,t);}
    private static WeaponSkillKeyframeTimeline cut(boolean wood,boolean skill){return new WeaponSkillKeyframeTimeline(8,REST,GRIP,
            k(0,REST,LINEAR),k(.5f,new WeaponSkillPose(-22,-72,wood?36:10,wood?.25f:2.35f,wood?2.6f:3.8f,.3f),EASE_OUT_CUBIC),
            k(1.48f,new WeaponSkillPose(wood?-27:-36,-83,wood?12:46,wood?2.25f:-1.85f,wood?3.6f:2.45f,skill?-3.9f:-2.8f),EASE_IN_QUAD),
            k(wood?3.1f:3.65f,new WeaponSkillPose(-16,-86,27,wood?1.65f:-.15f,3.1f,-.8f),EASE_OUT_CUBIC),k(8,REST,SMOOTH));}
    static void applyBodyPose(HumanoidModel<?> model,LivingEntity entity,String id,float t){
        boolean wood=WOOD_SWING.equals(id)||WOOD_BLESS.equals(id);float sign=entity.getMainArm()==HumanoidArm.RIGHT?1:-1;
        float w=Mth.clamp(t/.065f,0,1)*(1-Mth.clamp((t-.4f)/.6f,0,1));var arm=sign>0?model.rightArm:model.leftArm;
        arm.xRot=Mth.lerp(w,arm.xRot,wood?-1.1f:-1.3f);arm.yRot=Mth.lerp(w,arm.yRot,sign*(wood?-.2f:.23f));arm.zRot=Mth.lerp(w,arm.zRot,sign*.12f);
        model.body.yRot+=w*sign*(wood?-1:1)*(2*Mth.clamp(t/.185f,0,1)-1)*.1f;
    }
    private static WeaponSkillKeyframeTimeline.Keyframe k(float t,WeaponSkillPose p,WeaponSkillKeyframeTimeline.Easing e){return new WeaponSkillKeyframeTimeline.Keyframe(t,p,e);}
}
