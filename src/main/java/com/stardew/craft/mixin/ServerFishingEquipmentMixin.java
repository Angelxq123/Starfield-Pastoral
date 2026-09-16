package com.stardew.craft.mixin;

import com.stardew.craft.fishing.server.FishingSessionManager;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Check each equipment packet on the server thread, including rod -> rod -> original in one tick. */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerFishingEquipmentMixin {
	@Shadow public ServerPlayer player;

	@Inject(method = "handleSetCarriedItem", at = @At("RETURN"))
	private void stardewcraft$fishingSlot(ServerboundSetCarriedItemPacket packet, CallbackInfo ci) {
		FishingSessionManager.get(player.server).cancelIfInvalid(player);
	}

	@Inject(method = "handlePlayerAction", at = @At("RETURN"))
	private void stardewcraft$fishingHand(ServerboundPlayerActionPacket packet, CallbackInfo ci) {
		FishingSessionManager.get(player.server).cancelIfInvalid(player);
	}

	@Inject(method = "handleContainerClick", at = @At("RETURN"))
	private void stardewcraft$fishingInventory(ServerboundContainerClickPacket packet, CallbackInfo ci) {
		FishingSessionManager.get(player.server).cancelIfInvalid(player);
	}
}
