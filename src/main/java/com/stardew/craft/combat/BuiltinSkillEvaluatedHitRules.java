package com.stardew.craft.combat;

import net.minecraft.server.level.ServerPlayer;

/** Pre-application effects owned by active authored skill execution state. */
final class BuiltinSkillEvaluatedHitRules {
    private BuiltinSkillEvaluatedHitRules() {
    }

    static void emitSteelSpineStrike(EvaluatedWeaponHit hit) {
        if (hit.steelSpineBoost() != null
                && hit.successful()
                && hit.attacker() instanceof ServerPlayer player) {
            com.stardew.craft.combat.skill.WeaponSkillAnimationDispatcher.sendSkillAnim(player,"iron_edge",
                    hit.steelSpineBoost().strong()?"steel_spine_fury":"steel_spine_fury_weak",8);
        }
    }

}
