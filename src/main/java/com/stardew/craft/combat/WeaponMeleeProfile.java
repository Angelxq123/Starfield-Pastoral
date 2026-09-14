package com.stardew.craft.combat;

import com.stardew.craft.item.weapon.WeaponRegistry;

/** Common presentation identity for registered melee weapons; unknown weapons retain a safe fallback. */
public record WeaponMeleeProfile(WeaponType type, Material material) {
    public enum Material {
        METAL(0xBCDDEB), WOOD(0xD8B777), BONE(0xE5CE9F), FROST(0x8DDAFF),
        NATURE(0xA9E68D), WATER(0x57DCD9), FIRE(0xFF9C37), VOID(0xAB8EFA), HOLY(0xFFE3A0), PRISM(0xE2A6F3);

        private final int color;
        Material(int color) { this.color = color; }
        public int red() { return color >> 16 & 255; }
        public int green() { return color >> 8 & 255; }
        public int blue() { return color & 255; }
    }

    public static WeaponMeleeProfile get(String weaponId) {
        var data = WeaponRegistry.get(weaponId);
        if (data == null || data.getWeaponType() == WeaponType.SLINGSHOT) return null;
        Material material = switch (weaponId) {
            case "wood_club", "wood_mallet", "wooden_blade", "leahs_whittler", "elliotts_pencil", "alexs_bat", "sams_old_guitar" -> Material.WOOD;
            case "dragontooth_club", "femur", "bone_sword", "ossified_blade", "dragontooth_cutlass", "dragontooth_shiv" -> Material.BONE;
            case "yeti_tooth", "crystal_dagger" -> Material.FROST;
            case "forest_sword", "insect_head", "elf_blade", "wicked_kris" -> Material.NATURE;
            case "neptunes_glaive", "broken_trident", "wind_spire" -> Material.WATER;
            case "lava_katana", "tempered_broadsword" -> Material.FIRE;
            case "obsidian_edge", "dark_sword", "shadow_dagger", "sebs_lost_mace", "abbys_planchette",
                    "galaxy_sword", "galaxy_dagger", "galaxy_hammer", "infinity_blade", "infinity_dagger", "infinity_gavel" -> Material.VOID;
            case "holy_blade", "templars_blade" -> Material.HOLY;
            case "meowmere" -> Material.PRISM;
            default -> Material.METAL;
        };
        return new WeaponMeleeProfile(data.getWeaponType(), material);
    }

    public static boolean isSample(String weaponId) {
        return "lava_katana".equals(weaponId) || "carving_knife".equals(weaponId) || "femur".equals(weaponId);
    }
}
