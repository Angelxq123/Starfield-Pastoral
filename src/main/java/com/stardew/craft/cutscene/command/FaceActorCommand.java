package com.stardew.craft.cutscene.command;

import com.stardew.craft.cutscene.runtime.EventPlayer;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Mob;

/**
 * face_actor: naturally turns an actor to face a direction (yaw).
 * JSON: { "cmd": "face_actor", "actor": "alice", "yaw": 180 }
 * Or: { "cmd": "face_actor", "actor": "alice", "face_actor": "bob" } to face another actor.
 * The special tag "player" targets the local player.
 */
public class FaceActorCommand implements EventCommand {

    private final String actorTag;
    private final Float yaw;
    private final String faceTarget;
    private net.minecraft.world.entity.LivingEntity actor;
    private float finalYaw;
    private boolean done;

    public FaceActorCommand(String actorTag, Float yaw, String faceTarget) {
        this.actorTag = actorTag;
        this.yaw = yaw;
        this.faceTarget = faceTarget;
    }

    @Override
    public void start(EventPlayer player) {
        // Resolve source position/rotation target (may be player or actor).
        done = true;
        if ("player".equals(actorTag)) {
            actor = Minecraft.getInstance().player;
        } else {
            actor = player.getActor(actorTag);
        }
        if (actor == null) return;

        if (faceTarget != null) {
            net.minecraft.world.entity.LivingEntity target;
            if ("player".equals(faceTarget)) {
                target = Minecraft.getInstance().player;
            } else {
                target = player.getActor(faceTarget);
            }
            if (target == null) return;
            double dx = target.getX() - actor.getX();
            double dz = target.getZ() - actor.getZ();
            finalYaw = (float) (Math.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0f;
        } else if (yaw != null) {
            finalYaw = yaw;
        } else {
            return;
        }

        // The real player's camera remains under its existing explicit facing contract.
        if ("player".equals(actorTag)) {
            actor.setYRot(finalYaw);
            actor.setYHeadRot(finalYaw);
        } else {
            done = false;
        }
    }

    @Override
    public void tick(EventPlayer player) {
        if (done || actor == null) return;
        float facing = net.minecraft.util.Mth.approachDegrees(actor.getYRot(), finalYaw, ActorWalkPace.TURN_PER_TICK);
        actor.setYRot(facing);
        actor.setYHeadRot(facing);
        if (actor instanceof Mob mob) mob.setYBodyRot(facing);
        done = Math.abs(net.minecraft.util.Mth.wrapDegrees(finalYaw - facing)) < .01;
    }

    @Override
    public boolean isComplete() { return done; }
}
