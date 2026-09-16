package com.stardew.craft.block.crop;

import com.stardew.craft.block.decor.GardenPlanterBlock;
import com.stardew.craft.block.shape.ModelVoxelShapeCache;
import com.stardew.craft.block.utility.GardenPotBlock;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** One enclosing box, using the same stage selection and support pose as the rendered crop. */
public final class CropModelShapes {
    private CropModelShapes() {}

    @Nullable
    static VoxelShape shape(BlockState state, BlockGetter level, BlockPos pos) {
        boolean tall = state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF);
        boolean upper = tall && state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER;
        String key = "age=" + state.getValue(StardewCropBlock.AGE)
                + ",growth_stage=" + state.getValue(StardewCropBlock.GROWTH_STAGE);
        var color = state.getBlock().getStateDefinition().getProperty("color");
        if (color != null) key += ",color=" + state.getValue(color);
        if (state.hasProperty(FiberCropBlock.SEASON)) key += ",season=" + state.getValue(FiberCropBlock.SEASON);
        if (state.hasProperty(WildSeedCropBlock.WILD_VARIANT)) key += ",wild_variant=" + state.getValue(WildSeedCropBlock.WILD_VARIANT);
        if (tall) key += ",half=lower";
        String model = ModelVoxelShapeCache.variantModel(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString(), key);
        if (model == null || !model.startsWith("stardewcraft:block/crop3d/")) return null;
        VoxelShape shape = ModelVoxelShapeCache.requiredShape(model);
        BlockPos root = upper ? pos.below() : pos;
        BlockPos soil = root.below();
        BlockState support = level.getBlockState(soil);
        double offset = 0;
        boolean pot = support.getBlock() instanceof GardenPotBlock;
        if (!pot && support.getBlock() instanceof GardenPlanterBlock) offset = -.25;
        else if (!pot && support.getBlock() instanceof FarmBlock) {
            VoxelShape floor = support.getCollisionShape(level, soil);
            if (!floor.isEmpty()) offset = floor.max(Direction.Axis.Y) - 1;
        }
        return place(shape.bounds(), offset, pot, tall, upper);
    }

    public static VoxelShape place(AABB bounds, double offset, boolean pot, boolean tall, boolean upper) {
        if (pot) {
            double scale = 13.0 / 16;
            bounds = new AABB((bounds.minX - .5) * scale + .5, bounds.minY * scale - 5.0 / 16,
                    (bounds.minZ - .5) * scale + .5, (bounds.maxX - .5) * scale + .5,
                    bounds.maxY * scale - 5.0 / 16, (bounds.maxZ - .5) * scale + .5);
        } else bounds = bounds.move(0, offset, 0);
        if (tall) {
            double min = upper ? Math.max(1, bounds.minY) : bounds.minY;
            double max = upper ? bounds.maxY : Math.min(1, bounds.maxY);
            if (min >= max) return Shapes.empty();
            bounds = new AABB(bounds.minX, min, bounds.minZ, bounds.maxX, max, bounds.maxZ);
            if (upper) bounds = bounds.move(0, -1, 0);
        }
        return Shapes.create(bounds);
    }
}
