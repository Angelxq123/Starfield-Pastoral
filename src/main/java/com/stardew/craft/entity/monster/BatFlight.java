package com.stardew.craft.entity.monster;

import com.stardew.craft.monster.MonsterFlightMotion;
import net.minecraft.util.RandomSource;

/** Bat species parameters; source 2D formula remains in the dedicated reference test fixture. */
public final class BatFlight extends MonsterFlightMotion {
    public static final int STEPS_PER_TICK=3;
    public static final double WAKE_SECONDS=.56;
    public BatFlight() { super(1,5,5,.55); }
    public void variant(boolean iridium,boolean deepRed) {
        double extra=deepRed?3:iridium?1:0;
        configure(1+extra,5+extra,deepRed?8:5,.55);
    }
    public void initialize(RandomSource random) { slipperiness(random.nextInt(15,26)); }
}
