package com.stardew.craft.mixin;

import com.stardew.craft.block.mine.MineRockClumpBlock;
import com.stardew.craft.block.mine.MineStoneBlock;
import com.stardew.craft.mining.MineRockClumpMining;
import com.stardew.craft.mining.MineStoneMining;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Use the vanilla hold counter, rather than accumulated floats, for source-timed mine nodes. */
@Mixin(MultiPlayerGameMode.class)
public abstract class MineNodeClientProgressMixin {
    @Shadow @Final private Minecraft minecraft;
    @Shadow private float destroyProgress;
    @Shadow private float destroyTicks;

    @Redirect(method = "continueDestroyBlock", at = @At(value = "FIELD",
            target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;destroyProgress:F",
            opcode = Opcodes.PUTFIELD, ordinal = 0))
    private void stardewcraft$setTimedNodeProgress(MultiPlayerGameMode instance, float value,
                                                  BlockPos pos, Direction direction) {
        var state = minecraft.level.getBlockState(pos);
        if (value > destroyProgress && state.getBlock() instanceof MineStoneBlock) {
            int duration = MineStoneMining.breakTicks(state.getValue(MineStoneBlock.STONE_HEALTH),
                    minecraft.player.getMainHandItem());
            destroyProgress = MineStoneMining.progressAfterTicks((int) destroyTicks + 1, duration);
        } else if (value > destroyProgress && state.getBlock() instanceof MineRockClumpBlock clump) {
            destroyProgress = MineStoneMining.progressAfterTicks((int) destroyTicks + 1,
                    MineRockClumpMining.breakTicks(clump.sourceId(), minecraft.player.getMainHandItem()));
        } else {
            destroyProgress = value;
        }
    }
}
