package com.stardew.craft.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.ai.goal.EatBlockGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Ocelot;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.GrassBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Let grass variants share bonemeal patches and vanilla animal grazing/path preferences. */
@Mixin({GrassBlock.class, EatBlockGoal.class, Animal.class, Ocelot.class, AbstractHorse.class})
public abstract class GrassBlockBehaviorMixin {
    @WrapOperation(method = "*", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/state/BlockState;is(Lnet/minecraft/world/level/block/Block;)Z"))
    private static boolean stardewcraft$grassVariants(BlockState state, Block expected, Operation<Boolean> original) {
        return expected instanceof GrassBlock && state.getBlock() instanceof GrassBlock || original.call(state, expected);
    }
}
