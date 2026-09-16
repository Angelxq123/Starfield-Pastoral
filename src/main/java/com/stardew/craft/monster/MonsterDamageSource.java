package com.stardew.craft.monster;

import net.minecraft.world.damagesource.DamageSource;

/** Carries the attack's own base damage across the existing player damage pipeline. */
public final class MonsterDamageSource extends DamageSource {
    public enum Kind { CONTACT, PROJECTILE }
    private final java.util.Set<java.util.UUID> contacts = new java.util.HashSet<>();
    private final Kind kind;
    private final float baseDamage;
    public MonsterDamageSource(DamageSource transport, Kind kind, float baseDamage) {
        super(transport.typeHolder(), transport.getDirectEntity(), transport.getEntity());
        if (!Float.isFinite(baseDamage) || baseDamage < 0) throw new IllegalArgumentException("Invalid monster attack damage");
        this.kind = kind; this.baseDamage = baseDamage;
    }
    void deliverContact(net.minecraft.server.level.ServerPlayer player) {
        if (kind == Kind.CONTACT && getDirectEntity() instanceof StardewMonsterEntity monster
                && contacts.add(player.getUUID())) monster.onAcceptedContact(player);
    }
    public Kind kind() { return kind; }
    public float baseDamage() { return baseDamage; }
    public static float resolveBaseDamage(DamageSource source, float contactDamage) {
        if (source instanceof MonsterDamageSource monster) return monster.baseDamage;
        // Unmigrated attacks retain their existing rules until their species is migrated.
        return contactDamage;
    }
    public static MonsterDamageSource contact(StardewMonsterEntity monster) {
        return new MonsterDamageSource(monster.damageSources().mobAttack(monster), Kind.CONTACT,
                monster.monsterState().stats().getDamage());
    }
}
