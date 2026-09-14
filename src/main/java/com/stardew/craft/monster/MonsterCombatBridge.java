package com.stardew.craft.monster;

import com.stardew.craft.combat.equipment.CombatRingRules;
import com.stardew.craft.combat.equipment.EquipmentResolver;
import com.stardew.craft.combat.equipment.YobaProtectionState;
import com.stardew.craft.player.PlayerStardewDataAPI;
import com.stardew.craft.sound.ModSounds;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;

/** Runs after existing immunity/parry gates, before this hit can trigger Yoba or subtract HP. */
public final class MonsterCombatBridge {
    private MonsterCombatBridge() {}
    public static boolean beforePlayerDamage(DamageSource source, ServerPlayer player, int stardewHealth) {
        if (!(source instanceof MonsterDamageSource attack)) return false;
        if (source.getEntity() instanceof StardewMonsterEntity monster && monster.initialized()
                && monster.monsterState().context().generation() != null && MonsterFactory.ownedFloor(monster) == null) return true;
        attack.deliverContact(player);
        if (!EquipmentResolver.getMergedStats(player).hasYobaProtection()) return false;
        float chance = CombatRingRules.yobaProtectionChance(stardewHealth, PlayerStardewDataAPI.getLuckBuffLevel(player));
        if (player.getRandom().nextFloat() >= chance) return false;
        YobaProtectionState.start(player, player.level().getGameTime());
        player.playNotifySound(ModSounds.YOBA.get(), SoundSource.PLAYERS, 1, 1);
        return true;
    }
}
