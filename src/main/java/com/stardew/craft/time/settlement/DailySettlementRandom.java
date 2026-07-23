package com.stardew.craft.time.settlement;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

public final class DailySettlementRandom {
    private static final long POSITION_DOMAIN = 0x504F_5349_5449_4F4EL;
    private static final long ID_DOMAIN = 0x4944_5F53_5452_4541L;
    private static final long FNV_OFFSET_BASIS = 0xcbf29ce484222325L;
    private static final long FNV_PRIME = 0x100000001b3L;

    private DailySettlementRandom() {
    }

    public static RandomSource forPosition(
            long worldSeed,
            int absoluteDay,
            String subsystem,
            BlockPos pos) {
        Objects.requireNonNull(subsystem, "subsystem");
        Objects.requireNonNull(pos, "pos");
        return create(worldSeed, absoluteDay, subsystem, pos.asLong(), POSITION_DOMAIN);
    }

    public static RandomSource forId(
            long worldSeed,
            int absoluteDay,
            String subsystem,
            long stableId) {
        Objects.requireNonNull(subsystem, "subsystem");
        return create(worldSeed, absoluteDay, subsystem, stableId, ID_DOMAIN);
    }

    private static RandomSource create(
            long worldSeed,
            int absoluteDay,
            String subsystem,
            long stableKey,
            long domain) {
        long seed = mix64(worldSeed ^ domain);
        seed = mix64(seed ^ Integer.toUnsignedLong(absoluteDay));
        seed = mix64(seed ^ hashUtf8(subsystem));
        seed = mix64(seed ^ stableKey);
        return RandomSource.create(seed);
    }

    private static long hashUtf8(String value) {
        long hash = FNV_OFFSET_BASIS;
        for (byte current : value.getBytes(StandardCharsets.UTF_8)) {
            hash ^= current & 0xFFL;
            hash *= FNV_PRIME;
        }
        return mix64(hash);
    }

    private static long mix64(long value) {
        long mixed = value;
        mixed = (mixed ^ (mixed >>> 30)) * 0xbf58476d1ce4e5b9L;
        mixed = (mixed ^ (mixed >>> 27)) * 0x94d049bb133111ebL;
        return mixed ^ (mixed >>> 31);
    }
}
