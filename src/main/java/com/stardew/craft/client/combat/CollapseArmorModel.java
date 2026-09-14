package com.stardew.craft.client.combat;

import net.minecraft.client.model.HumanoidArmorModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.world.entity.LivingEntity;
import java.util.*;

/** Vanilla armor UVs/trim/glint stay in their own layer; only limb joints change. */
public final class CollapseArmorModel extends HumanoidArmorModel<LivingEntity> {
    public CollapseArmorModel(boolean inner) { super(root(inner?.5f:1));young=false; }
    private static ModelPart root(float grow) {
        var original=HumanoidArmorModel.createBodyLayer(new CubeDeformation(grow)).getRoot().bake(64,32);
        Map<String,ModelPart> parts=new HashMap<>();
        for(String name:List.of("head","hat","body"))parts.put(name,original.getChild(name));
        parts.put("left_arm",CollapsePlayerModel.limb(40,16,-1,-2,4,grow,true,5,2,32));
        parts.put("right_arm",CollapsePlayerModel.limb(40,16,-3,-2,4,grow,false,-5,2,32));
        parts.put("left_leg",CollapsePlayerModel.limb(0,16,-2,0,4,grow-.1f,true,1.9f,12,32));
        parts.put("right_leg",CollapsePlayerModel.limb(0,16,-2,0,4,grow-.1f,false,-1.9f,12,32));
        return new ModelPart(List.of(),parts);
    }
    public void follow(CollapsePlayerModel<?> player,HumanoidModel<?> visibility) {
        young=player.young;riding=player.riding;attackTime=player.attackTime;crouching=player.crouching;
        ModelPart[] source={player.head,player.hat,player.body,player.leftArm,player.rightArm,player.leftLeg,player.rightLeg};
        ModelPart[] visible={visibility.head,visibility.hat,visibility.body,visibility.leftArm,visibility.rightArm,visibility.leftLeg,visibility.rightLeg};
        ModelPart[] target={head,hat,body,leftArm,rightArm,leftLeg,rightLeg};
        for(int i=0;i<target.length;i++) {
            target[i].copyFrom(source[i]);
            target[i].visible=visible[i].visible;
            if(i>=3)target[i].getChild("bend").xRot=source[i].getChild("bend").xRot;
        }
    }
}
