package com.stardew.craft.client.combat;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.core.Direction;
import java.util.*;

/** Rigid upper/lower limbs, using the player's existing skin UVs and skin layers. */
public final class CollapsePlayerModel<T extends LivingEntity> extends PlayerModel<T> {
    private CollapseArmorModel innerSupport, outerSupport;
    public CollapseArmorModel armorSupport(boolean inner) {
        if(innerSupport==null) {innerSupport=new CollapseArmorModel(true);outerSupport=new CollapseArmorModel(false);}
        return inner?innerSupport:outerSupport;
    }
    public CollapsePlayerModel(boolean slim) {
        super(root(slim),slim);
        // EntityModel defaults to young=true. Players and rescue doubles are adults,
        // including when this model is evaluated without LivingEntityRenderer.
        young=false;
    }
    private static ModelPart root(boolean slim) {
        var original=PlayerModel.createMesh(CubeDeformation.NONE,slim).getRoot().bake(64,64);
        Map<String,ModelPart> parts=new HashMap<>();
        for(String name:List.of("head","hat","body","left_arm","right_arm","left_leg","right_leg",
                "ear","cloak","left_sleeve","right_sleeve","left_pants","right_pants","jacket"))
            parts.put(name,original.getChild(name));
        int width=slim?3:4;float armY=slim?2.5f:2;
        parts.put("left_arm",limb(32,48,-1,-2,width,0,false,5,armY,64));
        parts.put("right_arm",limb(40,16,slim?-2:-3,-2,width,0,false,-5,armY,64));
        parts.put("left_sleeve",limb(48,48,-1,-2,width,.25f,false,5,armY,64));
        parts.put("right_sleeve",limb(40,32,slim?-2:-3,-2,width,.25f,false,-5,armY,64));
        parts.put("left_leg",limb(16,48,-2,0,4,0,false,1.9f,12,64));
        parts.put("right_leg",limb(0,16,-2,0,4,0,false,-1.9f,12,64));
        parts.put("left_pants",limb(0,48,-2,0,4,.25f,false,1.9f,12,64));
        parts.put("right_pants",limb(0,32,-2,0,4,.25f,false,-1.9f,12,64));
        return new ModelPart(List.of(),parts);
    }
    /** Side UVs retain their original rows; terminal caps retain the original hand/sole pixels. */
    public static ModelPart limb(int u,int v,float x,float y,int width,float grow,boolean mirror,float px,float py,int textureHeight) {
        var sides=EnumSet.of(Direction.NORTH,Direction.SOUTH,Direction.EAST,Direction.WEST);
        List<ModelPart.Cube> upper=new ArrayList<>(),lower=new ArrayList<>();
        upper.add(cube(u,v,x,y,width,6,grow,mirror,textureHeight,sides));
        upper.add(cube(u,v,x,y,width,12,grow,mirror,textureHeight,EnumSet.of(Direction.UP)));
        upper.add(cube(u,v,x,y,width,6,grow,mirror,textureHeight,EnumSet.of(Direction.DOWN)));
        lower.add(cube(u,v+6,x,0,width,6,grow,mirror,textureHeight,sides));
        lower.add(cube(u,v,x,-6,width,12,grow,mirror,textureHeight,EnumSet.of(Direction.DOWN)));
        lower.add(cube(u,v+6,x,0,width,6,grow,mirror,textureHeight,EnumSet.of(Direction.UP)));
        ModelPart end=new ModelPart(lower,Map.of());end.setPos(0,y+6,0);end.setInitialPose(PartPose.offset(0,y+6,0));
        ModelPart start=new ModelPart(upper,Map.of("bend",end));start.setPos(px,py,0);start.setInitialPose(PartPose.offset(px,py,0));return start;
    }
    private static ModelPart.Cube cube(int u,int v,float x,float y,int width,int height,float grow,boolean mirror,int textureHeight,Set<Direction> faces) {
        return new ModelPart.Cube(u,v,x,y,-2,width,height,4,grow,grow,grow,mirror,64,textureHeight,faces);
    }
    public void bend(CombatCollapsePose.Frame pose) {
        bend(leftArm,pose.leftElbow());bend(leftSleeve,pose.leftElbow());
        bend(rightArm,pose.rightElbow());bend(rightSleeve,pose.rightElbow());
        bend(leftLeg,pose.leftKnee());bend(leftPants,pose.leftKnee());
        bend(rightLeg,pose.rightKnee());bend(rightPants,pose.rightKnee());
    }
    public static void bend(ModelPart part,float degrees) {
        part.getChild("bend").xRot=(float)Math.toRadians(degrees);
    }
    @Override public void translateToHand(HumanoidArm side,PoseStack stack) {
        super.translateToHand(side,stack);
        var lower=getArm(side).getChild("bend");
        lower.translateAndRotate(stack);
        stack.translate(0,-lower.y/16,0);
    }
}
