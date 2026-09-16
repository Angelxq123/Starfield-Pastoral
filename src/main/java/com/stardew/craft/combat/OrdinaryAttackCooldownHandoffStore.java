package com.stardew.craft.combat;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Bridges one successful ordinary Stardew hit to the next skill hit on the
 * same target. Youer keeps a shared recent-damage value on the target, so a
 * skill can otherwise be rejected before NeoForge's damage events run.
 */
public final class OrdinaryAttackCooldownHandoffStore {
    private static final long MAX_LIFETIME_TICKS = 10L;
    private static final State ACTIVE = new State();

    private OrdinaryAttackCooldownHandoffStore() {
    }

    public static void record(Player attacker, LivingEntity target, long hitTick) {
        if (attacker == null || target == null) {
            return;
        }
        ACTIVE.record(attacker.getUUID(), target.getUUID(), hitTick);
    }

    public static boolean consume(
            Player attacker,
            LivingEntity target,
            long nowTick
    ) {
        if (attacker == null || target == null) {
            return false;
        }
        return ACTIVE.consume(attacker.getUUID(), target.getUUID(), nowTick);
    }

    public static void clear(Player attacker) {
        if (attacker != null) {
            clear(attacker.getUUID());
        }
    }

    public static void clear(UUID attackerId) {
        if (attackerId != null) {
            ACTIVE.clear(attackerId);
        }
    }

    /** Package-private pure state seam for lifecycle and identity tests. */
    static final class State {
        private final Map<UUID, Handoff> handoffs = new HashMap<>();

        synchronized void record(
                UUID attackerId,
                UUID targetId,
                long hitTick
        ) {
            handoffs.put(
                    attackerId,
                    new Handoff(targetId, hitTick + MAX_LIFETIME_TICKS)
            );
        }

        synchronized boolean consume(
                UUID attackerId,
                UUID targetId,
                long nowTick
        ) {
            Handoff handoff = handoffs.get(attackerId);
            if (handoff == null) {
                return false;
            }
            if (handoff.expireTick() < nowTick) {
                handoffs.remove(attackerId);
                return false;
            }
            if (!handoff.targetId().equals(targetId)) {
                return false;
            }
            handoffs.remove(attackerId);
            return true;
        }

        synchronized void clear(UUID attackerId) {
            handoffs.remove(attackerId);
        }
    }

    private record Handoff(UUID targetId, long expireTick) {
    }
}
