package com.stardew.craft.client.npcnative;

import java.util.SplittableRandom;
import java.util.UUID;

/** Event-level randomness, stable per entity and independent of render frame rate and walking speed. */
public final class IdleBlinkClock {
    private final long seed;
    private final NativeNpcModel.Profile profile;
    private SplittableRandom random;
    private double start;
    private double duration;
    private double lastTime = -1;
    private boolean secondBlink;

    public IdleBlinkClock(UUID uuid, NativeNpcModel.Profile profile) {
        this.seed = uuid.getMostSignificantBits() ^ Long.rotateLeft(uuid.getLeastSignificantBits(), 23);
        this.profile = profile;
        reset();
    }

    private void reset() {
        random = new SplittableRandom(seed);
        start = random.nextDouble(0.8, profile.intervalMax());
        duration = 0.23 * random.nextDouble(profile.durationMin(), profile.durationMax());
        secondBlink = false;
    }

    /** Returns clip time in seconds, or -1 while the eyes are open between events. */
    public double sample(double seconds) {
        if (seconds < lastTime) reset();
        lastTime = seconds;
        while (seconds >= start + duration) {
            double end = start + duration;
            boolean doubleBlink = !secondBlink && random.nextDouble() < profile.doubleChance();
            start = end + (doubleBlink ? random.nextDouble(0.12, 0.22)
                    : random.nextDouble(profile.intervalMin(), profile.intervalMax()));
            duration = 0.23 * random.nextDouble(profile.durationMin(), profile.durationMax())
                    * (doubleBlink ? 0.88 : 1);
            secondBlink = doubleBlink;
        }
        return seconds < start ? -1 : (seconds - start) / duration * 0.23;
    }

    public double idleOffset() {
        return (seed >>> 11) * 0x1.0p-53 * 8;
    }
}
