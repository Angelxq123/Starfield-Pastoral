package com.stardew.craft.entity.npc;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;

/** Ground movement: collision stepping handles thresholds; navigation must route around higher obstacles. */
final class NpcMoveControl extends MoveControl {
    private boolean steering;

    NpcMoveControl(Mob mob) {
        super(mob);
    }

    @Override
    public void tick() {
        steering = false;
        if (operation != Operation.MOVE_TO) {
            super.tick();
            return;
        }
        operation = Operation.WAIT;
        double dx = wantedX - mob.getX();
        double dz = wantedZ - mob.getZ();
        if (dx * dx + dz * dz < MIN_SPEED_SQR) {
            mob.setZza(0.0F);
            return;
        }
        float yaw = (float) (Mth.atan2(dz, dx) * 180.0D / Math.PI) - 90.0F;
        steering = true;
        mob.setYRot(rotlerp(mob.getYRot(), yaw, MAX_TURN));
        // Do not travel sideways through a corner while still turning, or overshoot
        // a close alignment point at full walking speed and orbit it indefinitely.
        double alignment = Math.max(0, Math.cos(Math.toRadians(Mth.wrapDegrees(yaw - mob.getYRot()))));
        double approach = Math.min(1, Math.sqrt(dx * dx + dz * dz) / .35);
        mob.setSpeed((float) (speedModifier * mob.getAttributeValue(Attributes.MOVEMENT_SPEED) * alignment * approach));
        // Vanilla requests a jump from the tallest shape anywhere in the current cell,
        // including an edge the NPC does not touch. Do not use that heuristic for NPCs.
    }

    float collisionStepHeight(float maximum) {
        // Capability alone must not lift the body onto a clipped side obstacle.
        // Ground navigation supplies the actual support height of the current waypoint.
        if (!steering) return 0.0F;
        return (float) Mth.clamp(wantedY - mob.getY() + 1.0E-5D, 0.0D, maximum);
    }
}
