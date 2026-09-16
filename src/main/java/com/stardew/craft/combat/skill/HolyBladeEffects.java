package com.stardew.craft.combat.skill;

import com.stardew.craft.combat.CombatHealing;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

@SuppressWarnings("null")
public final class HolyBladeEffects {

    private HolyBladeEffects() {}

    public static void playHeal(ServerPlayer player, int amount) {
        if (player == null) {
            return;
        }
        if (CombatHealing.heal(player, amount) <= 0) return;

        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }

        Vec3 pos = player.position();
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER,
            pos.x, pos.y + player.getBbHeight() * 0.45, pos.z,
            4, 0.35, 0.3, 0.35, 0.02);
        level.sendParticles(ParticleTypes.END_ROD,
            pos.x, pos.y + player.getBbHeight() * 0.6, pos.z,
            4, 0.25, 0.4, 0.25, 0.02);

        player.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.15f, 1.4f);
        player.playNotifySound(SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.2f, 1.9f);
    }

    public static void playDodgeSuccess(ServerPlayer player) {
        if (player == null || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        Vec3 pos = player.position();
        level.sendParticles(ParticleTypes.CLOUD,
            pos.x, pos.y + player.getBbHeight() * 0.4, pos.z,
            12, 0.4, 0.2, 0.4, 0.02);
        level.sendParticles(ParticleTypes.END_ROD,
            pos.x, pos.y + player.getBbHeight() * 0.55, pos.z,
            8, 0.3, 0.35, 0.3, 0.02);

        player.playNotifySound(SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.6f, 1.8f);
        player.playNotifySound(SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.7f, 2.0f);
    }
}
