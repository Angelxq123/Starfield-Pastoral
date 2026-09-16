package com.stardew.craft.farm;

import com.stardew.craft.block.terrain.TerrainFarmlandBlock;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LegacyFarmSoilMigrationTest {
    private static Block farmland;

    @BeforeAll static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        var registry = (MappedRegistry<Block>) BuiltInRegistries.BLOCK;
        registry.unfreeze();
        farmland = Registry.register(registry, ResourceLocation.fromNamespaceAndPath("soil_test", "farmland"),
                new TerrainFarmlandBlock(Block.Properties.of()));
        farmland.getStateDefinition().getPossibleStates().forEach(state -> {
            state.initCache();
            Block.BLOCK_STATE_REGISTRY.add(state);
        });
        registry.freeze();
    }

    private static BlockState replace(BlockState state) {
        // Stand-in legacy identity; the target is the actual production farmland class.
        return LegacyFarmSoilMigration.replacement(state, Blocks.YELLOW_TERRACOTTA,
                Blocks.DIRT.defaultBlockState(), farmland.defaultBlockState());
    }

    @Test void convertsOnlyLegacyIdentitiesAndPreservesEveryMoistureLevel() {
        assertSame(Blocks.DIRT.defaultBlockState(), replace(Blocks.YELLOW_TERRACOTTA.defaultBlockState()));
        for (int moisture = 0; moisture <= 7; moisture++) {
            BlockState after = replace(Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, moisture));
            assertTrue(after.is(farmland));
            assertEquals(moisture, after.getValue(FarmBlock.MOISTURE));
            assertSame(after, replace(after));
        }
        for (Block block : new Block[]{Blocks.WHEAT, Blocks.STONE, Blocks.CHEST, Blocks.DIRT, Blocks.GRASS_BLOCK}) {
            assertSame(block.defaultBlockState(), replace(block.defaultBlockState()));
        }
    }

    @Test void clipsAllThreeAxesAndLeavesCropAboveSoilUntouched() {
        LevelChunkSection section = section();
        BlockState yellow = Blocks.YELLOW_TERRACOTTA.defaultBlockState();
        BlockPos origin = new BlockPos(20000, 0, 20000);
        section.setBlockState(1, 4, 1, yellow);
        section.setBlockState(2, 4, 1, Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7));
        section.setBlockState(2, 5, 1, Blocks.WHEAT.defaultBlockState());
        section.setBlockState(0, 4, 1, yellow); // Outside farm X boundary, same chunk.
        section.setBlockState(1, 4, 0, yellow); // Outside farm Z boundary.
        section.setBlockState(1, 3, 1, yellow); // Below the persisted farm bounds.
        section.setBlockState(1, 7, 1, yellow); // Above the persisted farm bounds.
        BlockPos min = origin.offset(1, 4, 1), max = origin.offset(15, 6, 15);
        var write = (java.util.function.BiConsumer<BlockPos, BlockState>) (pos, state) ->
                section.setBlockState(pos.getX()-origin.getX(), pos.getY(), pos.getZ()-origin.getZ(), state);
        assertEquals(2, LegacyFarmSoilMigration.migrateSection(section, origin, min, max,
                LegacyFarmSoilMigrationTest::replace, write));
        assertTrue(section.getBlockState(1, 4, 1).is(Blocks.DIRT));
        assertTrue(section.getBlockState(2, 4, 1).is(farmland));
        assertEquals(7, section.getBlockState(2, 4, 1).getValue(FarmBlock.MOISTURE));
        assertTrue(section.getBlockState(2, 5, 1).is(Blocks.WHEAT));
        for (int[] pos : new int[][]{{0,4,1}, {1,4,0}, {1,3,1}, {1,7,1}}) {
            assertSame(yellow, section.getBlockState(pos[0], pos[1], pos[2]));
        }
        assertEquals(0, LegacyFarmSoilMigration.migrateSection(section, origin, min, max,
                LegacyFarmSoilMigrationTest::replace, write));
    }

    @Test void skipsUnrelatedSectionsWithoutWritingAnything() {
        LevelChunkSection section = section();
        section.setBlockState(0, 0, 0, Blocks.YELLOW_TERRACOTTA.defaultBlockState());
        assertEquals(0, LegacyFarmSoilMigration.migrateSection(section, new BlockPos(0, 32, 0),
                BlockPos.ZERO, new BlockPos(15, 15, 15), state -> fail("Out-of-bounds section scanned"),
                (pos, state) -> fail("Out-of-bounds section changed")));
        assertEquals(0, LegacyFarmSoilMigration.migrateSection(section(), BlockPos.ZERO,
                BlockPos.ZERO, new BlockPos(15, 15, 15), LegacyFarmSoilMigrationTest::replace,
                (pos, state) -> fail("Empty section changed")));
    }

    private static LevelChunkSection section() {
        return new LevelChunkSection(new PalettedContainer<>(Block.BLOCK_STATE_REGISTRY,
                Blocks.AIR.defaultBlockState(), PalettedContainer.Strategy.SECTION_STATES), null);
    }
}
