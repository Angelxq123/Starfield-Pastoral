package com.stardew.craft.farm;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OfflineFarmCatchUpPlanTest {

    @Test
    void retainsOnlyMatchingDimensionAndInclusiveNormalizedXZBounds() {
        GlobalPos lowerCorner = position(Level.OVERWORLD, -10, 500, -20);
        GlobalPos upperCorner = position(Level.OVERWORLD, 10, -500, 20);
        GlobalPos wrongDimension = position(Level.NETHER, 0, 64, 0);
        GlobalPos outsideX = position(Level.OVERWORLD, 11, 64, 0);
        GlobalPos outsideZ = position(Level.OVERWORLD, 0, 64, -21);

        OfflineFarmCatchUpPlan plan = OfflineFarmCatchUpPlan.create(
                Level.OVERWORLD,
                new BlockPos(10, -100, 20),
                new BlockPos(-10, 100, -20),
                List.of(upperCorner, wrongDimension, outsideX, lowerCorner, outsideZ),
                List.of(),
                List.of()
        );

        assertEquals(List.of(lowerCorner, upperCorner), plan.crops());
    }

    @Test
    void sortsEveryRetainedListByXThenYThenZRegardlessOfInputOrder() {
        GlobalPos first = position(Level.OVERWORLD, -1, 20, 5);
        GlobalPos second = position(Level.OVERWORLD, 2, -10, 9);
        GlobalPos third = position(Level.OVERWORLD, 2, 3, -4);
        GlobalPos fourth = position(Level.OVERWORLD, 2, 3, 8);
        List<GlobalPos> expected = List.of(first, second, third, fourth);
        Set<GlobalPos> forward = new LinkedHashSet<>(expected);
        Set<GlobalPos> reverse = new LinkedHashSet<>(List.of(fourth, third, second, first));

        OfflineFarmCatchUpPlan forwardPlan = createWithAllPositions(forward);
        OfflineFarmCatchUpPlan reversePlan = createWithAllPositions(reverse);

        assertEquals(expected, forwardPlan.crops());
        assertEquals(expected, forwardPlan.trees());
        assertEquals(expected, forwardPlan.sprinklers());
        assertEquals(forwardPlan, reversePlan);
    }

    @Test
    void cropsRequireOnlyTheirContainingChunksAcrossBlockBoundaries() {
        OfflineFarmCatchUpPlan plan = OfflineFarmCatchUpPlan.create(
                Level.OVERWORLD,
                new BlockPos(-100, 0, -100),
                new BlockPos(100, 0, 100),
                List.of(
                        position(Level.OVERWORLD, -1, 0, -1),
                        position(Level.OVERWORLD, 15, 0, 15),
                        position(Level.OVERWORLD, 16, 0, 16)
                ),
                List.of(),
                List.of()
        );

        assertEquals(Set.of(
                new ChunkPos(-1, -1),
                new ChunkPos(0, 0),
                new ChunkPos(1, 1)
        ), plan.requiredChunks());
    }

    @Test
    void treesRequireEveryChunkIntersectingEightBlockRadius() {
        GlobalPos tree = position(Level.OVERWORLD, 8, 70, 8);

        OfflineFarmCatchUpPlan plan = OfflineFarmCatchUpPlan.create(
                Level.OVERWORLD,
                tree.pos(),
                tree.pos(),
                List.of(),
                List.of(tree),
                List.of()
        );

        assertEquals(Set.of(
                new ChunkPos(0, 0),
                new ChunkPos(0, 1),
                new ChunkPos(1, 0),
                new ChunkPos(1, 1)
        ), plan.requiredChunks());
    }

    @Test
    void sprinklersRequireEveryChunkIntersectingTwoBlockRadius() {
        GlobalPos sprinkler = position(Level.OVERWORLD, 14, 70, 14);

        OfflineFarmCatchUpPlan plan = OfflineFarmCatchUpPlan.create(
                Level.OVERWORLD,
                sprinkler.pos(),
                sprinkler.pos(),
                List.of(),
                List.of(),
                List.of(sprinkler)
        );

        assertEquals(Set.of(
                new ChunkPos(0, 0),
                new ChunkPos(0, 1),
                new ChunkPos(1, 0),
                new ChunkPos(1, 1)
        ), plan.requiredChunks());
    }

    @Test
    void requiredChunksAreDeduplicatedAcrossSystems() {
        GlobalPos shared = position(Level.OVERWORLD, 8, 64, 8);

        OfflineFarmCatchUpPlan plan = OfflineFarmCatchUpPlan.create(
                Level.OVERWORLD,
                shared.pos(),
                shared.pos(),
                List.of(shared),
                List.of(shared),
                List.of(shared)
        );

        assertEquals(4, plan.requiredChunks().size());
        assertEquals(Set.of(
                new ChunkPos(0, 0),
                new ChunkPos(0, 1),
                new ChunkPos(1, 0),
                new ChunkPos(1, 1)
        ), plan.requiredChunks());
    }

    @Test
    void emptyInputsProduceAnEmptyPlan() {
        OfflineFarmCatchUpPlan plan = OfflineFarmCatchUpPlan.create(
                Level.OVERWORLD,
                BlockPos.ZERO,
                BlockPos.ZERO,
                List.of(),
                List.of(),
                List.of()
        );

        assertEquals(List.of(), plan.crops());
        assertEquals(List.of(), plan.trees());
        assertEquals(List.of(), plan.sprinklers());
        assertEquals(Set.of(), plan.requiredChunks());
    }

    @Test
    void copiesInputsAndPublishesOnlyImmutableCollections() {
        GlobalPos crop = position(Level.OVERWORLD, 1, 2, 3);
        List<GlobalPos> crops = new ArrayList<>(List.of(crop));

        OfflineFarmCatchUpPlan plan = OfflineFarmCatchUpPlan.create(
                Level.OVERWORLD,
                BlockPos.ZERO,
                new BlockPos(10, 10, 10),
                crops,
                List.of(crop),
                List.of(crop)
        );
        crops.clear();

        assertEquals(List.of(crop), plan.crops());
        assertThrows(UnsupportedOperationException.class, () -> plan.crops().add(crop));
        assertThrows(UnsupportedOperationException.class, () -> plan.trees().clear());
        assertThrows(UnsupportedOperationException.class, () -> plan.sprinklers().clear());
        assertThrows(UnsupportedOperationException.class, () -> plan.requiredChunks().clear());
    }

    @Test
    void rejectsNullFactoryArguments() {
        BlockPos min = BlockPos.ZERO;
        BlockPos max = new BlockPos(10, 10, 10);
        List<GlobalPos> positions = List.of();

        assertThrows(NullPointerException.class,
                () -> OfflineFarmCatchUpPlan.create(null, min, max, positions, positions, positions));
        assertThrows(NullPointerException.class,
                () -> OfflineFarmCatchUpPlan.create(Level.OVERWORLD, null, max, positions, positions, positions));
        assertThrows(NullPointerException.class,
                () -> OfflineFarmCatchUpPlan.create(Level.OVERWORLD, min, null, positions, positions, positions));
        assertThrows(NullPointerException.class,
                () -> OfflineFarmCatchUpPlan.create(Level.OVERWORLD, min, max, null, positions, positions));
        assertThrows(NullPointerException.class,
                () -> OfflineFarmCatchUpPlan.create(Level.OVERWORLD, min, max, positions, null, positions));
        assertThrows(NullPointerException.class,
                () -> OfflineFarmCatchUpPlan.create(Level.OVERWORLD, min, max, positions, positions, null));
    }

    private static OfflineFarmCatchUpPlan createWithAllPositions(Set<GlobalPos> positions) {
        return OfflineFarmCatchUpPlan.create(
                Level.OVERWORLD,
                new BlockPos(-100, -100, -100),
                new BlockPos(100, 100, 100),
                positions,
                positions,
                positions
        );
    }

    private static GlobalPos position(net.minecraft.resources.ResourceKey<Level> dimension, int x, int y, int z) {
        return GlobalPos.of(dimension, new BlockPos(x, y, z));
    }
}
