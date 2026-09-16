package com.stardew.craft.combat;

/** Preserves native and Bukkit protection while restoring authored weapon damage. */
final class NativeWeaponDamageBridge {
    private NativeWeaponDamageBridge() {
    }

    static float applyProtectionRatio(
            float authoredDamage,
            float nativeInputDamage,
            float protectedNativeDamage
    ) {
        if (!Float.isFinite(authoredDamage)
                || !Float.isFinite(nativeInputDamage)
                || !Float.isFinite(protectedNativeDamage)
                || authoredDamage <= 0.0F
                || nativeInputDamage <= 0.0F
                || protectedNativeDamage <= 0.0F) {
            return 0.0F;
        }
        float ratio = protectedNativeDamage / nativeInputDamage;
        float result = authoredDamage * ratio;
        return Float.isFinite(result) ? Math.max(0.0F, result) : 0.0F;
    }
}
