package com.stardew.craft.combat;

import com.stardew.craft.combat.equipment.EquipmentResolver;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Server-authoritative recovery gate for ordinary Stardew weapon swings.
 *
 * <p>The gate rejects an early swing instead of letting Minecraft's partial
 * attack-strength value scale an otherwise complete Stardew damage roll.
 * Authored skill damage does not enter this class.</p>
 */
public final class StardewWeaponAttackRecovery {
    private static final Map<UUID, RecoveryState> RECOVERY_STATES =
            new HashMap<>();

    private StardewWeaponAttackRecovery() {
    }

    public static boolean tryAcquire(
            ServerPlayer player,
            ItemStack weapon
    ) {
        WeaponStats stats = WeaponStats.fromItemStack(weapon);
        float equipmentSpeed = EquipmentResolver.getMergedStats(player)
                .getWeaponSpeedMultiplier();
        return tryAcquire(
                player.getUUID(),
                Integer.toUnsignedLong(player.server.getTickCount()),
                recoveryIntervalTicks(stats, equipmentSpeed)
        );
    }

    /**
     * Compatibility overload for callers compiled against 0.5.4-0.5.6.
     * Dimension game time is deliberately ignored because it may pause or
     * move backwards when a player changes Stardew dimensions.
     */
    public static boolean tryAcquire(
            ServerPlayer player,
            ItemStack weapon,
            long ignoredDimensionTick
    ) {
        return tryAcquire(player, weapon);
    }

    static double recoveryIntervalTicks(
            WeaponStats stats,
            float equipmentSpeedMultiplier
    ) {
        return StardewWeaponSpeedRules.repeatMillisecondsFromRawSpeed(
                stats.getWeaponType(),
                stats.getRawSpeed(),
                equipmentSpeedMultiplier
                        + stats.getWeaponSpeedMultiplier()
        ) / 50.0D;
    }

    static int recoveryTicks(
            WeaponStats stats,
            float equipmentSpeedMultiplier
    ) {
        return StardewWeaponSpeedRules.recoveryTicksFromRawSpeed(
                stats.getWeaponType(),
                stats.getRawSpeed(),
                equipmentSpeedMultiplier
                        + stats.getWeaponSpeedMultiplier()
        );
    }

    static int recoveryTicks(
            WeaponType weaponType,
            int stardewSpeed,
            float equipmentSpeedMultiplier
    ) {
        return StardewWeaponSpeedRules.recoveryTicks(
                weaponType,
                stardewSpeed,
                equipmentSpeedMultiplier
        );
    }

    static synchronized boolean tryAcquire(
            UUID playerId,
            long nowTick,
            double recoveryTicks
    ) {
        double interval = Double.isFinite(recoveryTicks)
                ? Math.max(1.0D, recoveryTicks)
                : 1.0D;
        RecoveryState state = RECOVERY_STATES.get(playerId);
        if (state != null && nowTick < state.lastObservedTick()) {
            state = null;
        }
        if (state != null && nowTick + 1.0E-9D < state.readyTick()) {
            RECOVERY_STATES.put(
                    playerId,
                    new RecoveryState(state.readyTick(), nowTick)
            );
            return false;
        }
        double scheduleBase = state != null
                && nowTick - state.readyTick() < interval
                ? state.readyTick()
                : nowTick;
        RECOVERY_STATES.put(
                playerId,
                new RecoveryState(
                        Math.min(
                                (double) Long.MAX_VALUE,
                                scheduleBase + interval
                        ),
                        nowTick
                )
        );
        return true;
    }

    public static synchronized void clear(UUID playerId) {
        RECOVERY_STATES.remove(playerId);
    }

    private record RecoveryState(double readyTick, long lastObservedTick) {}

}
