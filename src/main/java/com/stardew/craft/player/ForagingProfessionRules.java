package com.stardew.craft.player;

import net.minecraft.server.level.ServerPlayer;

/** Shared settlement rules for player-collected forage. */
public final class ForagingProfessionRules {
    public static final double GATHERER_DOUBLE_CHANCE = 0.20D;

    private ForagingProfessionRules() {}

    public static boolean hasGathererBonus(ServerPlayer player, double roll) {
        return player != null
                && PlayerStardewDataAPI.hasProfession(player, ProfessionType.GATHERER)
                && roll >= 0.0D
                && roll < GATHERER_DOUBLE_CHANCE;
    }

    public static int applyGatherer(ServerPlayer player, int baseCount, double roll) {
        if (baseCount <= 0) return 0;
        return hasGathererBonus(player, roll) ? baseCount * 2 : baseCount;
    }
}
