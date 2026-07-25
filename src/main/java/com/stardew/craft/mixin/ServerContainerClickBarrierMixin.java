package com.stardew.craft.mixin;

import com.stardew.craft.time.settlement.DailySettlementServices;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.FilteredText;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Blocks the vanilla container click path for participants held by the settlement barrier. */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerContainerClickBarrierMixin {
    @Shadow public ServerPlayer player;

    @Inject(
            method = {
                    "handlePlayerAction",
                    "handlePickItem",
                    "handleRenameItem",
                    "handleSetBeaconPacket",
                    "handleSelectTrade",
                    "handleContainerSlotStateChanged",
                    "handleSetCarriedItem",
                    "handleContainerClick",
                    "handlePlaceRecipe",
                    "handleContainerButtonClick",
                    "handleSetCreativeModeSlot"
            },
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/network/protocol/PacketUtils;"
                            + "ensureRunningOnSameThread("
                            + "Lnet/minecraft/network/protocol/Packet;"
                            + "Lnet/minecraft/network/PacketListener;"
                            + "Lnet/minecraft/server/level/ServerLevel;)V",
                    shift = At.Shift.AFTER),
            cancellable = true)
    private void stardewcraft$blockLockedInventoryMutation(CallbackInfo ci) {
        DailySettlementServices.Services services =
                DailySettlementServices.find(player.server);
        if (services == null
                || !services.accessGuard().isGameplayAllowed(player.getUUID())) {
            if (services != null) {
                services.accessGuard().closeContainerIfLocked(player);
            }
            ci.cancel();
        }
    }

    @ModifyArg(
            method = "handleEditBook",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/concurrent/CompletableFuture;"
                            + "thenAcceptAsync(Ljava/util/function/Consumer;"
                            + "Ljava/util/concurrent/Executor;)"
                            + "Ljava/util/concurrent/CompletableFuture;"),
            index = 0)
    private Consumer<List<FilteredText>> stardewcraft$guardBookMutation(
            Consumer<List<FilteredText>> original) {
        return filteredText -> {
            DailySettlementServices.Services services =
                    DailySettlementServices.find(player.server);
            if (services != null
                    && services.accessGuard().isGameplayAllowed(player.getUUID())) {
                original.accept(filteredText);
            }
        };
    }
}
