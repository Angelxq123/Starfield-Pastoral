package com.stardew.craft.mixin;

import com.stardew.craft.core.ModDimensions;
import com.stardew.craft.weather.WeatherManager;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FarmBlock.class)
public class FarmBlockMixin {

    /**
     * 禁止星露谷维度的耕地自动湿润（通过附近的水）
     * 这样耕地就不会自动变成湿润耕地，哪怕旁边有水
     */
    @Inject(method = "isNearWater", at = @At("HEAD"), cancellable = true)
    private static void isNearWater(LevelReader level, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (level instanceof Level l && l.dimension().location().equals(ModDimensions.STARDEW_VALLEY.location())) {
            cir.setReturnValue(false);
        }
    }

    /**
     * 禁止星露谷维度的耕地退化为泥土（因为干涸）
     */
    @Inject(method = "turnToDirt", at = @At("HEAD"), cancellable = true)
    private static void turnToDirt(Entity entity, BlockState state, Level level, BlockPos pos, CallbackInfo ci) {
        if (level.dimension().location().equals(ModDimensions.STARDEW_VALLEY.location())) {
            ci.cancel();
        } else if (state.getBlock() instanceof com.stardew.craft.block.terrain.TerrainFarmlandBlock) {
            BlockState dirt = com.stardew.craft.block.terrain.TerrainSoils.substrate(state).defaultBlockState();
            level.setBlockAndUpdate(pos, dirt);
            level.gameEvent(net.minecraft.world.level.gameevent.GameEvent.BLOCK_CHANGE, pos,
                    net.minecraft.world.level.gameevent.GameEvent.Context.of(entity, dirt));
            ci.cancel();
        }
    }

    /**
     * 禁止随机刻改变湿度（原版会随着随机刻将湿度降维0）
     * 我们要在过夜时统一变为0（除非有保水土壤）
     * 
     * 在 randomTick 中，原版逻辑是：
     * 1. 并没有 setMoisture 的单独方法，是直接 level.setBlock(..., state.setValue(MOISTURE, newAmount), 2)
     * 2. 我们通过 Inject randomTick 并取消大部分逻辑来实现
     */
    @SuppressWarnings("null")
    @Inject(method = "randomTick", at = @At("HEAD"), cancellable = true)
    public void randomTick(BlockState state, net.minecraft.server.level.ServerLevel level, BlockPos pos, net.minecraft.util.RandomSource random, CallbackInfo ci) {
        if (level.dimension().location().equals(ModDimensions.STARDEW_VALLEY.location())) {
            // 星露谷维度中：
            // 1. 下雨时会自动湿润 (原版逻辑) -> 我们保留这个逻辑？
            //    原版Stardew下雨天不需要浇水。Minecraft randomTick里会检查 isRainingAt(pos.above())
            
            // 星露谷规则：雨天无条件湿润所有耕地（不检查天空遮挡）
            // 注意：必须用 WeatherManager 判断，不能用 level.isRaining()，
            // 因为后者读的是所有维度共享的 PrimaryLevelData 天气（= 主世界天气）。
            if (!WeatherManager.isRaining(level)) {
                // 非雨天，禁止随机刻改变湿度（保持当前湿度）
                ci.cancel(); 
            } else {
                // 雨天，允许原版逻辑执行（会将 moisture 设为 7）
                // 注意：原版 randomTick 内部还会调用 isRainingAt 检查天空，
                // 为保证无遮挡要求也能湿润，直接手动设置 moisture=7 并 cancel。
                level.setBlock(pos, state.setValue(net.minecraft.world.level.block.FarmBlock.MOISTURE, 7), 2);
                ci.cancel();
            }
        }
    }
}
