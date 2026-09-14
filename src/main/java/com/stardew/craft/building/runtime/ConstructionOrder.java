package com.stardew.craft.building.runtime;

import net.minecraft.nbt.CompoundTag;

/** Progress is persisted before world completion; completed work can be retried after unloading. */
public record ConstructionOrder(int remainingDays, int lastProcessedDay, boolean scaffoldReady) {
    public ConstructionOrder {
        if (remainingDays < 0 || remainingDays > 3 || lastProcessedDay < 1) {
            throw new IllegalArgumentException("Invalid construction clock");
        }
    }
    public ConstructionOrder onDay(int day, boolean workingDay) {
        if (day <= lastProcessedDay) return this;
        return new ConstructionOrder(Math.max(0, remainingDays - (workingDay ? 1 : 0)), day, scaffoldReady);
    }
    public ConstructionOrder through(int day, java.util.function.IntPredicate workingDay) {
        if (day <= lastProcessedDay) return this;
        int remaining = remainingDays, cursor = lastProcessedDay;
        while (cursor < day && remaining > 0) if (workingDay.test(++cursor)) remaining--;
        return new ConstructionOrder(remaining, day, scaffoldReady);
    }
    public ConstructionOrder withScaffold() { return new ConstructionOrder(remainingDays, lastProcessedDay, true); }
    public CompoundTag save() {
        var tag = new CompoundTag();
        tag.putInt("Days", remainingDays); tag.putInt("LastDay", lastProcessedDay); tag.putBoolean("Scaffold", scaffoldReady);
        return tag;
    }
    public static ConstructionOrder load(CompoundTag tag) {
        return new ConstructionOrder(tag.getInt("Days"), tag.getInt("LastDay"), tag.getBoolean("Scaffold"));
    }
}
