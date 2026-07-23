package com.stardew.craft.time.settlement;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DailySettlementRandomTest {
    private static final long WORLD_SEED = 0x1234_5678_9ABC_DEF0L;

    @Test
    void positionInputsProduceRepeatableMultiValueStreams() {
        BlockPos pos = new BlockPos(17, 64, -29);

        assertArrayEquals(
                take(DailySettlementRandom.forPosition(WORLD_SEED, 91, "pasture_grass", pos)),
                take(DailySettlementRandom.forPosition(WORLD_SEED, 91, "pasture_grass", pos)));
    }

    @Test
    void idInputsProduceRepeatableMultiValueStreams() {
        assertArrayEquals(
                take(DailySettlementRandom.forId(WORLD_SEED, 91, "animal_growth", 8472L)),
                take(DailySettlementRandom.forId(WORLD_SEED, 91, "animal_growth", 8472L)));
    }

    @Test
    void everyPositionSeedComponentChangesTheStream() {
        long[] baseline = take(DailySettlementRandom.forPosition(
                WORLD_SEED, 91, "wild_tree_seed", new BlockPos(17, 64, -29)));

        assertDifferent(baseline, take(DailySettlementRandom.forPosition(
                WORLD_SEED + 1, 91, "wild_tree_seed", new BlockPos(17, 64, -29))));
        assertDifferent(baseline, take(DailySettlementRandom.forPosition(
                WORLD_SEED, 92, "wild_tree_seed", new BlockPos(17, 64, -29))));
        assertDifferent(baseline, take(DailySettlementRandom.forPosition(
                WORLD_SEED, 91, "pasture_grass", new BlockPos(17, 64, -29))));
        assertDifferent(baseline, take(DailySettlementRandom.forPosition(
                WORLD_SEED, 91, "wild_tree_seed", new BlockPos(18, 64, -29))));
    }

    @Test
    void everyIdSeedComponentChangesTheStream() {
        long[] baseline = take(DailySettlementRandom.forId(
                WORLD_SEED, 91, "fish_pond", 8472L));

        assertDifferent(baseline, take(DailySettlementRandom.forId(
                WORLD_SEED + 1, 91, "fish_pond", 8472L)));
        assertDifferent(baseline, take(DailySettlementRandom.forId(
                WORLD_SEED, 92, "fish_pond", 8472L)));
        assertDifferent(baseline, take(DailySettlementRandom.forId(
                WORLD_SEED, 91, "animal_growth", 8472L)));
        assertDifferent(baseline, take(DailySettlementRandom.forId(
                WORLD_SEED, 91, "fish_pond", 8473L)));
    }

    @Test
    void objectStreamsDoNotDependOnProcessingOrder() {
        List<Long> forward = List.of(7L, 11L, 19L, 23L);
        List<Long> reverse = List.of(23L, 19L, 11L, 7L);
        long[][] first = streamsFor(forward);
        long[][] second = streamsFor(reverse);

        for (int index = 0; index < forward.size(); index++) {
            long id = forward.get(index);
            assertArrayEquals(first[index], second[reverse.indexOf(id)]);
        }
    }

    @Test
    void rejectsNullSubsystemsAndPositions() {
        assertThrows(NullPointerException.class,
                () -> DailySettlementRandom.forPosition(WORLD_SEED, 1, null, BlockPos.ZERO));
        assertThrows(NullPointerException.class,
                () -> DailySettlementRandom.forPosition(WORLD_SEED, 1, "grass", null));
        assertThrows(NullPointerException.class,
                () -> DailySettlementRandom.forId(WORLD_SEED, 1, null, 3L));
    }

    private static long[][] streamsFor(List<Long> ids) {
        long[][] streams = new long[ids.size()][];
        for (int index = 0; index < ids.size(); index++) {
            streams[index] = take(DailySettlementRandom.forId(
                    WORLD_SEED, 17, "order_independence", ids.get(index)));
        }
        return streams;
    }

    private static long[] take(RandomSource random) {
        long[] values = new long[12];
        for (int index = 0; index < values.length; index++) {
            values[index] = random.nextLong();
        }
        return values;
    }

    private static void assertDifferent(long[] first, long[] second) {
        assertFalse(Arrays.equals(first, second), "expected distinct random streams");
    }
}
