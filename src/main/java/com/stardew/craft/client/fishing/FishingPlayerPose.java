package com.stardew.craft.client.fishing;

import net.minecraft.client.model.PlayerModel;
import org.joml.Vector3f;

/** Maps the authored torso onto the vanilla body that owns the third-person arm layer. */
public final class FishingPlayerPose {
    private FishingPlayerPose() {}

    public static void apply(PlayerModel<?> model,FishingRigPose pose,boolean mirrored) {
        model.rightArm.visible=model.leftArm.visible=model.rightSleeve.visible=model.leftSleeve.visible=false;
        int torso=pose.index("torso");
        var top=pose.skin[torso].transformPosition(new Vector3f(0,24,0));
        var angles=pose.world[torso].getEulerAnglesZYX(new Vector3f());
        model.body.setPos(mirrored?top.x:-top.x,24-top.y,top.z);
        model.body.xRot=-angles.x;model.body.yRot=mirrored?angles.y:-angles.y;model.body.zRot=mirrored?-angles.z:angles.z;
        model.jacket.copyFrom(model.body);
        model.head.setPos(mirrored?top.x:-top.x,24-top.y,top.z);model.hat.copyFrom(model.head);
    }
}
