package com.stardew.craft.combat;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class JinxDefenseTest {
    @Test void customHealthProtectionPreservesSignedDefenseThroughDamageSettlement(){
        for(float defense:new float[]{-8,-3,0,2}) {
            var protection=new WeaponCombatEvents.CustomHealthProtection(1,1,1,1,defense);
            var request=DamageRequest.builder("shaman_contact")
                    .sourceKind(DamageRequest.SourceKind.MONSTER_ATTACK).baseDamage(18,18)
                    .defense(protection.defense(),false)
                    .defenseRule(DamageRequest.DefenseRule.STARDEW_PLAYER_DEFENSE).build();
            assertEquals(defense,protection.defense());
            assertEquals(18-defense,DamagePipeline.evaluate(request,()->0).getFinalDamage());
        }
    }
    @Test void invalidNumbersAndNegativeMultipliersRemainRejected(){
        for(float invalid:new float[]{Float.NaN,Float.POSITIVE_INFINITY,Float.NEGATIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class,()->new WeaponCombatEvents.CustomHealthProtection(1,1,1,1,invalid));
        }
        assertThrows(IllegalArgumentException.class,()->new WeaponCombatEvents.CustomHealthProtection(-1,1,1,1,-8));
        assertThrows(IllegalArgumentException.class,()->new WeaponCombatEvents.CustomHealthProtection(1,-1,1,1,-8));
        assertThrows(IllegalArgumentException.class,()->new WeaponCombatEvents.CustomHealthProtection(1,1,-1,1,-8));
        assertThrows(IllegalArgumentException.class,()->new WeaponCombatEvents.CustomHealthProtection(1,1,1,-1,-8));
    }
    @Test void curseSubtractsEightDefenseEvenBelowZero(){
        assertEquals(-8,DamagePipeline.calculateDefenseReduction(18,-8,DamageRequest.DefenseRule.STARDEW_PLAYER_DEFENSE,0));
        assertEquals(-3,DamagePipeline.calculateDefenseReduction(18,5-8,DamageRequest.DefenseRule.STARDEW_PLAYER_DEFENSE,.8F));
        assertEquals(2,DamagePipeline.calculateDefenseReduction(18,10-8,DamageRequest.DefenseRule.STARDEW_PLAYER_DEFENSE,0));
        assertEquals(0,DamagePipeline.calculateDefenseReduction(18,-8,DamageRequest.DefenseRule.FIXED_RESILIENCE,0));
    }
}
