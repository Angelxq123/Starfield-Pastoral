package com.stardew.craft.combat.skill.handler;

/** Server-owned windup cadence. Damage follows at the authored contact tick. */
final class HeavyHammerPoundCadence {
    private double next;
    private long leaseUntil = Long.MIN_VALUE;
    private boolean held, queued;

    HeavyHammerPoundCadence(long startTick) {
        next = startTick + HeavyHammerRules.BURST_START + HeavyHammerRules.BURST_INTERVAL - HeavyHammerRules.POUND_WINDUP;
    }

    void input(long now, boolean down) {
        if (down) {
            if (!held) queued = true;
            leaseUntil = now + 8;
        }
        held = down;
    }

    boolean advance(long now, double interval, boolean blocked) {
        if (now > leaseUntil) { held = false; queued = false; }
        if (blocked) { queued = false; return false; }
        if (!(held || queued) || now + 1.0E-8 < next) return false;
        queued = false;
        // Preserve fractional attack periods, but never catch up after an interruption.
        next = (now - next < 1 ? next : now) + HeavyHammerRules.burstInterval(interval);
        return true;
    }
}
