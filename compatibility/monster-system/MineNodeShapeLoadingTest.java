package com.stardew.craft.monster;

import com.stardew.craft.block.mine.MineStoneBlock;
import com.stardew.craft.block.shape.ModelVoxelShapeCache;
import net.minecraft.core.registries.BuiltInRegistries;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.concurrent.Executors;
import static org.junit.jupiter.api.Assertions.*;

class MineNodeShapeLoadingTest {
    @Test void requiredNodeRecoversPreviouslyCachedEmptyResult() throws Exception {
        String model="stardewcraft:block/mine/nodes/stone_32";
        var expected=ModelVoxelShapeCache.requiredShape(model).bounds();
        var field=ModelVoxelShapeCache.class.getDeclaredField("SHAPE_CACHE");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        var cache=(java.util.Map<String,net.minecraft.world.phys.shapes.VoxelShape>)field.get(null);
        cache.put(model,net.minecraft.world.phys.shapes.Shapes.empty());
        assertEquals(expected,ModelVoxelShapeCache.requiredShape(model).bounds());
        String missing="stardewcraft:block/mine/nodes/missing_regression_test";
        assertThrows(IllegalStateException.class,()->ModelVoxelShapeCache.requiredShape(missing));
        assertFalse(cache.containsKey(missing),"Required failures must remain retryable");
    }
    @Test void coldParallelLoadsNeverCacheEmptyMineNodes() throws Exception {
        var models=new ArrayList<String>();
        for(var block:BuiltInRegistries.BLOCK)if(block instanceof MineStoneBlock stone)
            models.add("stardewcraft:block/mine/nodes/stone_"+MineStoneBlock.modelStem(stone.sourceId()));
        assertTrue(models.size()>40);
        var expected=new java.util.HashMap<String,net.minecraft.world.phys.AABB>();
        for(var model:models)expected.put(model,ModelVoxelShapeCache.requiredShape(model).bounds());
        try(var workers=Executors.newFixedThreadPool(8)){
            for(int round=0;round<8;round++){
                ModelVoxelShapeCache.clearAll();
                var jobs=new ArrayList<java.util.concurrent.Future<?>>();
                for(int repeat=0;repeat<2;repeat++)for(var model:models)jobs.add(workers.submit(()->{
                    var shape=ModelVoxelShapeCache.requiredShape(model);
                    assertFalse(shape.isEmpty(),model);assertEquals(expected.get(model),shape.bounds(),model);
                }));
                for(var job:jobs)job.get();
            }
        }
    }
}
