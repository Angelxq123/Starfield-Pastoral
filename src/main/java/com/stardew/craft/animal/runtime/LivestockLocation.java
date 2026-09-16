package com.stardew.craft.animal.runtime;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

/** Last simulated position and environmental clock, independent of entity/chunk lifetime. */
public record LivestockLocation(BlockPos position, BlockPos homeAnchor, boolean outside, int environmentStamp, boolean leftOut) {
    public CompoundTag save() {
        var tag = new CompoundTag(); tag.putLong("Position", position.asLong()); tag.putLong("HomeAnchor", homeAnchor.asLong());
        tag.putBoolean("Outside", outside); tag.putInt("EnvironmentStamp", environmentStamp); tag.putBoolean("LeftOut", leftOut); return tag;
    }
    public static LivestockLocation load(CompoundTag tag) { return new LivestockLocation(BlockPos.of(tag.getLong("Position")), BlockPos.of(tag.getLong("HomeAnchor")), tag.getBoolean("Outside"), tag.getInt("EnvironmentStamp"), tag.getBoolean("LeftOut")); }
}
