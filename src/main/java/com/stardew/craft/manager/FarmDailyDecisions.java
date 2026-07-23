package com.stardew.craft.manager;

import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

import java.util.Objects;

final class FarmDailyDecisions {
    private FarmDailyDecisions() {
    }

    static int firstAnimalDayToProcess(int lastProcessedAbsDay, int absoluteDay) {
        int normalizedLastDay = lastProcessedAbsDay <= 0
                ? absoluteDay - 1
                : lastProcessedAbsDay;
        return normalizedLastDay + 1;
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

    static boolean rollWildSpread(RandomSource random, float chance) {
        return Objects.requireNonNull(random, "random").nextFloat() < chance;
    }

    static int rollWildOffset(RandomSource random) {
        return Mth.nextInt(Objects.requireNonNull(random, "random"), -3, 3);
    }
}
