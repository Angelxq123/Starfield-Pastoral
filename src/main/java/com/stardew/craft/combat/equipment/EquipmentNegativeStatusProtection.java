package com.stardew.craft.combat.equipment;

import com.stardew.craft.combat.debuff.ImmunitySystem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * One equipment decision for a composite custom harmful status.
 *
 * <p>Callers must reuse the returned duration for every server state,
 * accompanying mob effect and client payload that belongs to that one status.
 * This prevents one authored freeze from rolling immunity multiple times or
 * giving its visual and authoritative components different lifetimes.</p>
 */
public final class EquipmentNegativeStatusProtection {
    private EquipmentNegativeStatusProtection() {
    }

    public static Decision decide(
            LivingEntity target,
        int baseDurationTicks
    ) {
        if (baseDurationTicks <= 0) {
            return new Decision(true, 0, false);
        }
        if (!(target instanceof ServerPlayer player)) {
            return new Decision(false, baseDurationTicks, false);
        }

        EquipmentStats equipment = EquipmentResolver.getMergedStats(player);
        boolean resisted = ImmunitySystem.tryResistEffect(
                equipment.getImmunity()
        );
        return decide(
                baseDurationTicks,
                resisted,
                equipment.hasSturdy()
        );
    }

    static Decision decide(
            int baseDurationTicks,
            boolean resisted,
        boolean sturdy
    ) {
        if (baseDurationTicks <= 0 || resisted) {
            return new Decision(true, 0, false);
        }
        return new Decision(
                false,
                ImmunitySystem.adjustDurationTicks(
                        baseDurationTicks,
                        sturdy
                ),
                sturdy
        );
    }

    /** Source millisecond effects halve before the single conversion to Minecraft ticks. */
    public static Decision decideMilliseconds(LivingEntity target, int durationMs) {
        if (durationMs <= 0) return new Decision(true, 0, false);
        boolean resisted = false, sturdy = false;
        if (target instanceof ServerPlayer player) {
            var equipment = EquipmentResolver.getMergedStats(player);
            resisted = ImmunitySystem.tryResistEffect(equipment.getImmunity());
            sturdy = equipment.hasSturdy();
        }
        return millisecondsDecision(durationMs, resisted, sturdy);
    }
    public static Decision millisecondsDecision(int durationMs, boolean resisted, boolean sturdy) {
        if (durationMs <= 0 || resisted) return new Decision(true, 0, false);
        long adjusted = sturdy ? Math.max(1, durationMs / 2) : durationMs;
        return new Decision(false, (int) ((adjusted + 49) / 50), sturdy);
    }

    public record Decision(
            boolean resisted,
            int durationTicks,
            boolean durationReduced
    ) {
        /**
         * Applies this same equipment decision to another timer belonging to
         * the same composite status without rolling immunity again.
         */
        public int adjustRelatedDurationTicks(int baseDurationTicks) {
            if (resisted || baseDurationTicks <= 0) {
                return 0;
            }
            return ImmunitySystem.adjustDurationTicks(
                    baseDurationTicks,
                    durationReduced
            );
        }
    }
}
