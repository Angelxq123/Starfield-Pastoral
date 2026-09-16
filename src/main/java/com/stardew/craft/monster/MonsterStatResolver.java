package com.stardew.craft.monster;

import com.stardew.craft.combat.MonsterStats;
import net.minecraft.util.RandomSource;

/** Monster.parseMonsterInfo's progression pass; species modifiers are applied afterwards. */
public final class MonsterStatResolver {
    private MonsterStatResolver() {}
    public record Resolved(int initialHealth, MonsterStats combat) {}
    public static Resolved base(MonsterDefinition definition, MonsterSpawnContext context, RandomSource random) {
        int health = definition.health(), damage = definition.damage(), resilience = definition.resilience();
        float miss = definition.missChance();
        if (definition.mineMonster() && context.bottomReached()) {
            resilience += resilience / 2;
            miss = Math.min(1, miss * 2);
            health += random.nextInt(health);
            damage += damage / 2 > 0 ? random.nextInt(damage / 2) : 0;
        }
        return new Resolved(health, MonsterStats.builder().damage(damage).resilience(resilience)
                .missChance(miss).experience(definition.experience()).build());
    }
}
