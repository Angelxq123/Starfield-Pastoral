package com.stardew.craft.monster;

import net.minecraft.util.RandomSource;

/** Serpent's source acceleration/slipperiness, interpreted as a total three-dimensional speed. */
public final class SerpentFlightMotion extends MonsterFlightMotion {
    public SerpentFlightMotion() { super(2,7,7,.45); }
    public void initialize(RandomSource random) { slipperiness(random.nextInt(24,34)); }
}
