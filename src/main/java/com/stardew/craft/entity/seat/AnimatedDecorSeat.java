package com.stardew.craft.entity.seat;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/** Sub-tick contact point shared with the decorative model's renderer. */
public interface AnimatedDecorSeat {
    // Vanilla player render scale is 0.9375; use the rendered hips, not the unscaled model.
    double HIPS_FROM_FEET = 12.0 / 16 * .9375;
    double CONTACT_CLEARANCE = 1.0 / 64;
    Vec3 riderFeet(float partialTick);
    double riderAngle(float partialTick);
    Direction facing();
}
