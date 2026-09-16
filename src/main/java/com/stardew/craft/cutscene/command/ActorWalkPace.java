package com.stardew.craft.cutscene.command;

/** Units: blocks/tick for speed, blocks for distance. No timeline debt is accumulated. */
final class ActorWalkPace {
    static final double FALLBACK_SPEED = 0.045;
    static final double ACCELERATION = 0.006;
    static final float TURN_PER_TICK = 12;

    private ActorWalkPace() {}

    static double normalSpeed(double previewBlocksPerSecond, double stride, double requested) {
        double normal = Double.isFinite(previewBlocksPerSecond) && previewBlocksPerSecond > 0
                ? previewBlocksPerSecond / 20.0
                : Double.isFinite(stride) && stride > 0 ? stride / 20.0 : FALLBACK_SPEED;
        // Old scripts often request 0.11–0.4 blocks/tick. Ordinary walking never exceeds
        // the character's authored pace; an explicitly slower approach remains possible.
        return Double.isFinite(requested) && requested > 0 ? Math.min(normal, requested) : normal;
    }

    static double nextSpeed(double current, double maximum, double remaining, double yawError) {
        double target = Math.min(maximum, Math.sqrt(2 * ACCELERATION * Math.max(0, remaining)));
        // Turn in place when facing away, then gently enter the walk instead of skating sideways.
        target *= Math.max(0, Math.cos(Math.toRadians(yawError)));
        return Math.min(remaining, Math.max(0, Math.min(current + ACCELERATION, target)));
    }
}
