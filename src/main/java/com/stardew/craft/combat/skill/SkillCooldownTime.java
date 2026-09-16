package com.stardew.craft.combat.skill;

/** Arithmetic shared by the persisted server clock and the client clock. */
public final class SkillCooldownTime {

    private SkillCooldownTime() {}

    public static long durationBetween(long sourceNow, long sourceEnd) {
        if (sourceEnd <= sourceNow) {
            return 0L;
        }
        long duration = sourceEnd - sourceNow;
        return duration < 0L ? Long.MAX_VALUE : duration;
    }

    public static long endAt(long targetNow, long sourceNow, long sourceEnd) {
        long duration = durationBetween(sourceNow, sourceEnd);
        if (duration == 0L || targetNow == Long.MAX_VALUE) {
            return targetNow;
        }
        long end = targetNow + duration;
        return end < targetNow ? Long.MAX_VALUE : end;
    }
}
