package com.stardew.craft.gametest;

import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.decor.FarmTwigBlock;
import com.stardew.craft.farm.FarmDebrisDailyService;
import com.stardew.craft.farm.FarmDebrisPlacementRules;
import com.stardew.craft.farm.FarmInstance;
import com.stardew.craft.farm.FarmType;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

@GameTestHolder("stardewcraft_farm_debris")
@PrefixGameTestTemplate(false)
public final class FarmDebrisGameTests {
    private FarmDebrisGameTests() {
    }

    @GameTest(templateNamespace = "stardewcraft_farm_debris", template = "ring_utilities")
    public static void floorsAndOccupiedTilesBlockEveryFarmEcologyEntry(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(2, 1, 2));
        FarmInstance farm = new FarmInstance(
                UUID.randomUUID(), "debris-test", "debris-test", 0, origin, FarmType.STANDARD);
        BlockPos ground = origin.offset(4, 0, 4);
        BlockPos place = ground.above();

        level.setBlock(ground, ModBlocks.DIRT.get().defaultBlockState(), 3);
        for (int y = place.getY(); y <= farm.getFarmBoundsMax().getY(); y++) {
            level.removeBlock(new BlockPos(place.getX(), y, place.getZ()), false);
        }
        var bare = FarmDebrisPlacementRules.findBareSurface(
                level, farm, ground.getX(), ground.getZ());
        helper.assertTrue(bare != null && bare.place().equals(place),
                "Natural bare farm dirt was rejected");

        level.setBlock(place, ModBlocks.FLOORING_BLOCK.get().defaultBlockState(), 3);
        helper.assertTrue(FarmDebrisPlacementRules.findBareSurface(
                        level, farm, ground.getX(), ground.getZ()) == null,
                "Flooring was treated as a bare debris surface");
        helper.assertTrue(!FarmDebrisPlacementRules.canPlaceYoungTree(level, farm, place),
                "Wild tree seed could replace flooring");
        helper.assertTrue(!FarmDebrisPlacementRules.canSpreadDebrisAt(level, farm, place),
                "Daily debris could replace flooring");

        level.removeBlock(place, false);
        level.setBlock(ground, Blocks.DIRT_PATH.defaultBlockState(), 3);
        helper.assertTrue(FarmDebrisPlacementRules.findBareSurface(
                        level, farm, ground.getX(), ground.getZ()) == null,
                "A player path was treated as natural bare dirt");

        level.setBlock(ground, ModBlocks.DIRT.get().defaultBlockState(), 3);
        level.setBlock(place, Blocks.CHEST.defaultBlockState(), 3);
        helper.assertTrue(FarmDebrisPlacementRules.findBareSurface(
                        level, farm, ground.getX(), ground.getZ()) == null,
                "An occupied farm tile accepted random debris");
        helper.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_farm_debris", template = "ring_utilities")
    public static void dailyDebrisUsesFarmTwigsInsteadOfTreeLogs(GameTestHelper helper)
            throws ReflectiveOperationException {
        var method = FarmDebrisDailyService.class.getDeclaredMethod(
                "randomDebrisState", RandomSource.class);
        method.setAccessible(true);
        RandomSource random = RandomSource.create(294295L);
        boolean foundTwig = false;
        for (int i = 0; i < 128; i++) {
            BlockState state = (BlockState) method.invoke(null, random);
            boolean twig = state.getBlock() instanceof FarmTwigBlock;
            boolean stone = state.is(ModBlocks.MINE_STONE_343.get())
                    || state.is(ModBlocks.MINE_STONE_450.get());
            helper.assertTrue(twig || stone,
                    "Daily farm debris still emitted a tree log or unrelated block");
            foundTwig |= twig;
        }
        helper.assertTrue(foundTwig, "Daily farm debris never selected either twig variant");
        helper.succeed();
    }
}
