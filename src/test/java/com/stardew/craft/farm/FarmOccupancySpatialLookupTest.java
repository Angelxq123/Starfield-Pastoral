package com.stardew.craft.farm;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class FarmOccupancySpatialLookupTest {

    @Test
    void findsTheFarmThatContainsTheCurrentPosition() {
        FarmInstanceRegistry registry = new FarmInstanceRegistry();
        FarmInstance first = registry.createFarmAtDate(
            UUID.randomUUID(), "Leah", "Forest", FarmType.STANDARD, 1, 0);
        FarmInstance second = registry.createFarmAtDate(
            UUID.randomUUID(), "Sam", "River", FarmType.STANDARD, 1, 0);

        assertSame(first, FarmChunkManager.findContainingFarm(
            registry.getAllFarms(), first.getSpawnPoint()));
        assertSame(second, FarmChunkManager.findContainingFarm(
            registry.getAllFarms(), second.getSpawnPoint()));
    }

    @Test
    void returnsNullWhenThePositionIsOutsideEveryFarm() {
        FarmInstanceRegistry registry = new FarmInstanceRegistry();
        FarmInstance farm = registry.createFarmAtDate(
            UUID.randomUUID(), "Penny", "Hill", FarmType.STANDARD, 1, 0);
        BlockPos outside = farm.getFarmBoundsMax().offset(1, 0, 0);

        assertNull(FarmChunkManager.findContainingFarm(registry.getAllFarms(), outside));
    }
}
