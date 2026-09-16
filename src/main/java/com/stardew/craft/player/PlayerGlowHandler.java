package com.stardew.craft.player;

import com.stardew.craft.combat.equipment.EquipmentResolver;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;

/** Syncs equipped light strength; ring lighting must never place or remove world blocks. */
public final class PlayerGlowHandler {
    private PlayerGlowHandler() {}

    public static void tick(ServerPlayer player) {
        int light = player.isAlive() && !player.isSpectator()
                ? Mth.clamp(EquipmentResolver.getMergedStats(player).getLightLevel(), 0, 15) : 0;
        ((PlayerGlowState) player).stardewcraft$setRingLight(light);
    }

    public static void cleanup(ServerPlayer player) {
        ((PlayerGlowState) player).stardewcraft$setRingLight(0);
    }
    public static void onPlayerLeave(ServerPlayer player) {
        cleanup(player);
    }
}
