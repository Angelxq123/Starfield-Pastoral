package com.stardew.craft.gametest;

import com.stardew.craft.block.crop.StardewCropBlock;
import com.stardew.craft.block.shape.ModelVoxelShapeCache;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.AABB;

/** Verify selection against the shipped stage geometry, including crops shorter than one block. */
final class CropSelectionAssertions {
    private CropSelectionAssertions() {}

    static void matchesStage(GameTestHelper helper, BlockPos root) {
        var level = helper.getLevel();
        var state = level.getBlockState(root);
        String key = "age=" + state.getValue(StardewCropBlock.AGE)
                + ",growth_stage=" + state.getValue(StardewCropBlock.GROWTH_STAGE) + ",half=lower";
        String model = ModelVoxelShapeCache.variantModel(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString(), key);
        helper.assertTrue(model != null, "Stage model is missing");
        var support = level.getBlockState(root.below()).getCollisionShape(level, root.below());
        AABB expected = ModelVoxelShapeCache.requiredShape(model).bounds().move(0, support.max(Direction.Axis.Y) - 1, 0);
        var lower = state.getShape(level, root);
        var upper = level.getBlockState(root.above()).getShape(level, root.above());
        helper.assertTrue(!lower.isEmpty(), "Visible crop has no lower selection");
        helper.assertTrue(upper.isEmpty() == (expected.maxY <= 1), "Upper selection does not match the model height");
        AABB actual = upper.isEmpty() ? lower.bounds() : lower.bounds().minmax(upper.bounds().move(0, 1, 0));
        double error = Math.max(Math.max(Math.abs(actual.minX - expected.minX), Math.abs(actual.maxX - expected.maxX)),
                Math.max(Math.max(Math.abs(actual.minY - expected.minY), Math.abs(actual.maxY - expected.maxY)),
                        Math.max(Math.abs(actual.minZ - expected.minZ), Math.abs(actual.maxZ - expected.maxZ))));
        helper.assertTrue(error < 1e-5, "Crop selection no longer encloses the rendered stage");
    }
}
