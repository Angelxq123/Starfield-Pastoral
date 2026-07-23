package com.stardew.craft.fishpond.service;

import net.minecraft.util.RandomSource;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

final class FishPondDailyDecisions {
    private FishPondDailyDecisions() {
    }

    static long stableId(String pondId) {
        long hash = 0xcbf29ce484222325L;
        for (byte current : Objects.requireNonNull(pondId, "pondId")
                .getBytes(StandardCharsets.UTF_8)) {
            hash ^= current & 0xFFL;
            hash *= 0x100000001b3L;
        }
        return hash;
    }

    static boolean rollChance(RandomSource random, double chance) {
        return Objects.requireNonNull(random, "random").nextDouble() < chance;
    }
}
