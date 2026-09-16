package com.stardew.craft.monster;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/** Fly emergence precedes active steering; hits lock turning for the original 500ms. */
public final class FlySteering extends MonsterFlightMotion {
    private int spawnRemaining=1000;
    public FlySteering() { super(2,7,7,.4); }
    public void initialize(RandomSource random) { slipperiness(random.nextInt(14,34)); }
    public int spawnRemaining() { return spawnRemaining; }
    public void advance(int elapsed,Vec3 offset,boolean chase,boolean turningAllowed) {
        elapsed(elapsed);
        if(spawnRemaining>=0){spawnRemaining-=elapsed;super.steer(null,false,false);return;}
        super.steer(offset,chase,turningAllowed);
    }
    @Override public CompoundTag save(){var tag=super.save();tag.putInt("Spawn",spawnRemaining);return tag;}
    @Override public void load(CompoundTag tag){super.load(tag);spawnRemaining=tag.contains("Spawn")?tag.getInt("Spawn"):1000;}
}
