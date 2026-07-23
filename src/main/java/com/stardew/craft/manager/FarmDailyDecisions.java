package com.stardew.craft.manager;

import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

import java.util.Objects;

final class FarmDailyDecisions {
    private FarmDailyDecisions() {
    }

    static long firstAnimalDayToProcess(int lastProcessedAbsDay, int absoluteDay) {
        if (lastProcessedAbsDay > 0 && lastProcessedAbsDay >= absoluteDay) {
            return (long) absoluteDay + 1L;
        }
        long normalizedLastDay = lastProcessedAbsDay <= 0
                ? (long) absoluteDay - 1L
                : lastProcessedAbsDay;
        return normalizedLastDay + 1L;
    }

    static boolean rollGrassSource(RandomSource random) {
        return Objects.requireNonNull(random, "random").nextDouble() < 0.65D;
    }

    static boolean rollGrassNeighbor(RandomSource random) {
        return Objects.requireNonNull(random, "random").nextDouble() < 0.25D;
    }

    static int rollGrassVariant(RandomSource random) {
        return Objects.requireNonNull(random, "random").nextInt(3);
    }

    static boolean rollWildSeed(RandomSource random, float chance) {
        return Objects.requireNonNull(random, "random").nextFloat() < chance;
    }

    static WildSeedDailyState reconcileWildSeedState(
            boolean hasSeed,
            int lastSeedRollAbsDay,
            int lastShakenAbsDay,
            int absoluteDay,
            RandomSource random,
            float seedChance) {
        if (lastSeedRollAbsDay == absoluteDay) {
            return new WildSeedDailyState(hasSeed, lastSeedRollAbsDay, lastShakenAbsDay);
        }
        return new WildSeedDailyState(
                rollWildSeed(random, seedChance), absoluteDay, Integer.MIN_VALUE);
    }

    static boolean rollWildSpread(RandomSource random, float chance) {
        return Objects.requireNonNull(random, "random").nextFloat() < chance;
    }

    static int rollWildOffset(RandomSource random) {
        return Mth.nextInt(Objects.requireNonNull(random, "random"), -3, 3);
    }

    static WildPendingState onWildTreeTracked(
            boolean liveEntryExists,
            boolean pendingRemove,
            String pendingAddTreeId,
            String trackedTreeId) {
        Objects.requireNonNull(trackedTreeId, "trackedTreeId");
        if (pendingRemove) {
            return new WildPendingState(
                    true, pendingAddTreeId == null ? trackedTreeId : pendingAddTreeId);
        }
        if (pendingAddTreeId != null) {
            return new WildPendingState(false, pendingAddTreeId);
        }
        return new WildPendingState(false, liveEntryExists ? null : trackedTreeId);
    }

    static WildPendingState onWildTreeUntracked(
            boolean liveEntryExists,
            boolean pendingRemove,
            String pendingAddTreeId) {
        return new WildPendingState(liveEntryExists || pendingRemove, null);
    }

    static <T> T routeWildShakeEntry(T liveEntry, T pendingAddEntry) {
        return pendingAddEntry != null ? pendingAddEntry : liveEntry;
    }

    static WildShakeDecision applyWildShakeState(
            boolean hasSeed,
            int lastSeedRollAbsDay,
            int lastShakenAbsDay,
            int absoluteDay,
            boolean canDropSeed) {
        WildSeedDailyState current = new WildSeedDailyState(
                hasSeed, lastSeedRollAbsDay, lastShakenAbsDay);
        if (lastShakenAbsDay == absoluteDay) {
            return new WildShakeDecision(false, false, current);
        }
        boolean dropSeed = hasSeed && canDropSeed;
        return new WildShakeDecision(
                true,
                dropSeed,
                new WildSeedDailyState(
                        dropSeed ? false : hasSeed,
                        lastSeedRollAbsDay,
                        absoluteDay));
    }
}

record WildSeedDailyState(
        boolean hasSeed,
        int lastSeedRollAbsDay,
        int lastShakenAbsDay) {
}

record WildPendingState(boolean pendingRemove, String pendingAddTreeId) {
}

record WildShakeDecision(
        boolean accepted,
        boolean dropSeed,
        WildSeedDailyState state) {
}
