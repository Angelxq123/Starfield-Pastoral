package com.stardew.craft.mixin;

import com.stardew.craft.time.settlement.DailySettlementServices;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Blocks the vanilla container click path for participants held by the settlement barrier. */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerContainerClickBarrierMixin {
    @Shadow @Final private ServerPlayer player;

    @Inject(method = "handleContainerClick", at = @At("HEAD"), cancellable = true)
    private void stardewcraft$blockLockedContainerClick(
            ServerboundContainerClickPacket packet, CallbackInfo ci) {
        DailySettlementServices.Services services =
                DailySettlementServices.getForPlayer(player);
        if (!services.accessGuard().isGameplayAllowed(player.getUUID())) {
            services.accessGuard().closeContainerIfLocked(player);
            ci.cancel();
        }
    }
}
