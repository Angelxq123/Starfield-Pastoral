package com.stardew.craft.item.weapon;

import com.stardew.craft.data.VanillaObjectCatalog;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

/** SDV Slingshot.canThisBeAttached / GetAmmoDamage; quality never changes damage. */
public final class SlingshotAmmo {
    private SlingshotAmmo() {}
    public static String sourceId(ItemStack stack) {
        String namespace = BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace();
        if (!namespace.equals("stardewcraft") && !namespace.equals("minecraft")) return "";
        var row = VanillaObjectCatalog.resolve(stack);
        return row == null ? "" : row.key();
    }
    public static int baseDamage(ItemStack stack) {
        return switch (sourceId(stack)) {
            case "388" -> 2;
            case "390" -> 5;
            case "378" -> 10;
            case "380", "441" -> 20;
            case "384" -> 30;
            case "382" -> 15;
            case "386" -> 50;
            default -> 1;
        };
    }
    public static boolean accepts(ItemStack stack) {
        if (stack.isEmpty() || sourceId(stack).isEmpty()) return false;
        if (baseDamage(stack) > 1) return true;
        var row = VanillaObjectCatalog.resolve(stack);
        return row != null && (row.category() == -5 || row.category() == -79 || row.category() == -75);
    }
    public static boolean explosive(ItemStack stack) { return sourceId(stack).equals("441"); }
    public static boolean egg(ItemStack stack) {
        var row = VanillaObjectCatalog.resolve(stack);
        return row != null && row.category() == -5;
    }
    public static int rollDamage(ItemStack ammo, net.minecraft.util.RandomSource random, float attackMultiplier) {
        return rollDamage(ammo, random, attackMultiplier, 1);
    }
    public static int rollDamage(ItemStack ammo, net.minecraft.util.RandomSource random, float attackMultiplier, int slingshotMultiplier) {
        int d = baseDamage(ammo);
        return (int) (slingshotMultiplier * (float) (d + random.nextInt(d + 2 + d / 2) - d / 2) * (1 + attackMultiplier));
    }
}
