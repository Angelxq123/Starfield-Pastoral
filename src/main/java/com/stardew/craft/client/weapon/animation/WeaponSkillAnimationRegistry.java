package com.stardew.craft.client.weapon.animation;

import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

public final class WeaponSkillAnimationRegistry {

    private static final Map<String, WeaponSkillAnimation> SKILL_ANIMATIONS = new HashMap<>();
    private static final Map<String, WeaponSkillAnimation> WEAPON_ANIMATIONS = new HashMap<>();

    private static final WeaponSkillAnimation DEFAULT_ANIMATION = new RustySwordSkillAnimation();

    static {
        registerWeapon("rusty_sword", DEFAULT_ANIMATION);
        registerWeapon("pirate_sword", DEFAULT_ANIMATION);
        registerWeapon("silver_saber", DEFAULT_ANIMATION);
        registerWeapon("cutlass", DEFAULT_ANIMATION);
        registerWeapon("forest_sword", DEFAULT_ANIMATION);
        registerWeapon("bone_sword", DEFAULT_ANIMATION);
        registerWeapon("claymore", DEFAULT_ANIMATION);
        registerWeapon("neptunes_glaive", DEFAULT_ANIMATION);
        registerWeapon("templars_blade", DEFAULT_ANIMATION);
        registerWeapon("insect_head", DEFAULT_ANIMATION);
        registerWeapon("holy_blade", DEFAULT_ANIMATION);
        registerWeapon("tempered_broadsword", DEFAULT_ANIMATION);
        registerWeapon("steel_falchion", DEFAULT_ANIMATION);
        registerWeapon("dark_sword", DEFAULT_ANIMATION);
        registerWeapon("dragontooth_cutlass", DEFAULT_ANIMATION);
        registerWeapon("dwarf_sword", DEFAULT_ANIMATION);
        registerWeapon("galaxy_sword", DEFAULT_ANIMATION);
        registerWeapon("infinity_blade", DEFAULT_ANIMATION);
        registerWeapon("carving_knife", DEFAULT_ANIMATION);
        registerWeapon("iron_dirk", DEFAULT_ANIMATION);
        registerWeapon("wind_spire", DEFAULT_ANIMATION);
        registerWeapon("elf_blade", DEFAULT_ANIMATION);
        registerWeapon("burglars_shank", DEFAULT_ANIMATION);
        registerWeapon("crystal_dagger", DEFAULT_ANIMATION);
        registerWeapon("shadow_dagger", DEFAULT_ANIMATION);
        registerWeapon("broken_trident", DEFAULT_ANIMATION);
        registerWeapon("femur", DEFAULT_ANIMATION);
        registerWeapon("wicked_kris", DEFAULT_ANIMATION);
        registerWeapon("dwarf_dagger", DEFAULT_ANIMATION);
        registerWeapon("dragontooth_shiv", DEFAULT_ANIMATION);
        registerWeapon("iridium_needle", DEFAULT_ANIMATION);
        registerWeapon("galaxy_dagger", DEFAULT_ANIMATION);
        registerWeapon("infinity_dagger", DEFAULT_ANIMATION);
        registerSkill("iron_dirk", "iron_dirk_thrust", new IronWindAnimation("iron_dirk_thrust"));
        registerSkill("wind_spire", "wind_spire_thrust", new IronWindAnimation("wind_spire_thrust"));
        registerSkill("bone_sword", "bone_fracture", new BoneClaymoreAnimation("bone_fracture"));
        registerSkill("claymore", "claymore_foldback", new BoneClaymoreAnimation("claymore_foldback"));
        registerSkill("claymore", "claymore_foldback_return", new BoneClaymoreAnimation("claymore_foldback_return"));
        registerSkill("pirate_sword", "desperate_plunder", new PirateSilverAnimation("desperate_plunder"));
        for (String id : new String[]{"silver_foldback", "silver_foldback_return", "silver_foldback_stay", "silver_foldback_empty"})
            registerSkill("silver_saber", id, new PirateSilverAnimation(id));
        for(String id:new String[]{"steel_spine_fury_enter","steel_spine_fury","steel_spine_fury_weak"})registerSkill("iron_edge",id,new GuardSpineAnimation(id));
        registerSkill("rusty_sword","tetanus_strike",new RustWoodAnimation("tetanus_strike"));
        registerSkill("wooden_blade","tree_blessing",new RustWoodAnimation("tree_blessing"));
        for(String id:new String[]{"steel_falchion_line","steel_falchion_trace"})registerSkill("steel_falchion",id,new CrescentFalchionAnimation(id));
        registerSkill("cutlass", "crescent_slash", new CrescentFalchionAnimation("crescent_slash"));
        registerSkill("forest_sword", "forest_blessing_prepare", new GroveWeaponAnimation("forest_blessing_prepare"));
        registerSkill("forest_sword", "forest_blessing", new GroveWeaponAnimation("forest_blessing"));
        for (String id : new String[]{"dwarf_rune_guard", "dwarf_fortress", "dwarf_dagger_thrust", "dwarf_dagger_rush"})
            registerSkill(id.startsWith("dwarf_dagger") ? "dwarf_dagger" : "dwarf_sword", id, new DwarfWeaponAnimation(id));
        registerSkill("elf_blade", "elf_blade_leaf", new GroveWeaponAnimation("elf_blade_leaf"));
        registerSkill("steel_smallsword", "light_counter", new GuardSpineAnimation("light_counter"));
        registerSkill("steel_smallsword", "light_counter_counter", new GuardSpineAnimation("light_counter_counter"));
        registerSkill("carving_knife", "carving_thrust", new CarvingKnifeThrustAnimation());
        for (String id : new String[]{"iridium_needle_thrust", "iridium_needle_strike", "iridium_needle_final", "iridium_needle_frenzy"})
            registerSkill("iridium_needle", id, new NeedleBurglarAnimation(id));
        registerSkill("burglars_shank", "burglar_shank", new NeedleBurglarAnimation("burglar_shank"));
        registerSkill("templars_blade", "templar_vow", new SacredWeaponAnimation("templar_vow"));
        registerSkill("insect_head", "insect_eye_stance", new ShadowInsectAnimation("insect_eye_stance"));
        registerSkill("insect_head", "insect_dash", new ShadowInsectAnimation("insect_dash"));
        registerSkill("shadow_dagger", "shadow_dagger_execute", new ShadowInsectAnimation("shadow_dagger_execute"));
        registerSkill("holy_blade", "holy_smite", new SacredWeaponAnimation("holy_smite"));
        registerSkill("holy_blade", "holy_domain", new SacredWeaponAnimation("holy_domain"));
        for (String id : new String[]{"templar_vow_strike", "templar_vow_end", "templar_judgement"})
            registerSkill("templars_blade", id, new SacredWeaponAnimation(id));
        for (String id : new String[]{"dark_sword_blood_debt", "dark_sword_blood_moon", "tempered_quench", "tempered_billet"})
            registerSkill(id.startsWith("dark_sword") ? "dark_sword" : "tempered_broadsword", id, new BloodForgeAnimation(id));
        registerSkill("dragontooth_cutlass", "dragon_breath_judgement", new DragonCutlassAnimation(true));
        registerSkill("dragontooth_cutlass", "dragon_breath_thrust", new DragonCutlassAnimation(false));
        registerSkill("crystal_dagger","crystal_dagger_layer",new CrystalVenomAnimation("crystal_dagger_layer"));
        for(String id:new String[]{"wicked_kris_venom_ripple","wicked_kris_nest_burst"})
            registerSkill("wicked_kris",id,new CrystalVenomAnimation(id));
        registerSkill("dragontooth_club", "dragontooth_club_jaw", new DragonRapierAnimation("dragontooth_club_jaw"));
        registerSkill("dragontooth_club", "dragontooth_club_breath", new DragonRapierAnimation("dragontooth_club_breath"));
        registerSkill("rapier", "rapier_riposte", new DragonRapierAnimation("rapier_riposte"));
        registerSkill("the_slammer", "slammer_upheaval", new SlammerDwarfAnimation("slammer_upheaval"));
        registerSkill("the_slammer", "slammer_rampage", new SlammerDwarfAnimation("slammer_rampage"));
        registerSkill("dwarf_hammer", "dwarf_hammer_rebound", new SlammerDwarfAnimation("dwarf_hammer_rebound"));
        registerSkill("dwarf_hammer", "dwarf_hammer_faultline", new SlammerDwarfAnimation("dwarf_hammer_faultline"));
        registerSkill("lead_rod", "lead_rod_press", new IronClubAnimation("lead_rod_press"));
        registerSkill("kudgel", "kudgel_sweep", new IronClubAnimation("kudgel_sweep"));
        registerSkill("wood_club", "wood_club_whirl", new WoodWeaponAnimation("wood_club_whirl"));
        registerSkill("wood_mallet", "wood_mallet_leap", new WoodWeaponAnimation("wood_mallet_leap"));
        registerSkill("femur", "femur_slam", new FemurSlamSkillAnimation());
        for (String id : new String[]{com.stardew.craft.combat.skill.handler.HeavyHammerRules.SWEEP, com.stardew.craft.combat.skill.handler.HeavyHammerRules.QUAKE, com.stardew.craft.combat.skill.handler.HeavyHammerRules.PRESS, com.stardew.craft.combat.skill.handler.HeavyHammerRules.ENDLESS, com.stardew.craft.combat.skill.handler.HeavyHammerRules.POUND})
            registerSkill(id.startsWith("galaxy_") ? "galaxy_hammer" : "infinity_gavel", id, new HeavyHammerAnimation(id));
        registerSkill("lava_katana", "lava_katana_brand", LavaKatanaSlashAnimation.INSTANCE);
        registerSkill("lava_katana", "lava_katana_reverb", LavaKatanaReverbAnimation.INSTANCE);
        
        registerSkill("dragontooth_shiv", "dragontooth_shiv_stab", new DragontoothShivAnimation(false));
        registerSkill("dragontooth_shiv", "dragontooth_shiv_breath", new DragontoothShivAnimation(true));
        for (String skill : new String[]{"startrail_rift", "galaxy_judgement", "galaxy_dagger_ready", "galaxy_dagger_starstab", "galaxy_dagger_starleap"}) {
            registerSkill(skill.startsWith("galaxy_dagger") ? "galaxy_dagger" : "galaxy_sword", skill, new GalaxyWeaponAnimation(skill));
        }
        for (String skill : new String[]{"tide_mark", "tide_anchor", "fishcatch_ready", "fishcatch_thrust", "tide_reel"}) {
            registerSkill(skill.equals("tide_mark") || skill.equals("tide_anchor") ? "neptunes_glaive" : "broken_trident",
                    skill, new TideWeaponAnimation(skill));
        }
        for (String skill : new String[]{"singularity_evolve", "singularity_release", "eternal_collapse", "infinity_dagger_ready", "infinity_dagger_singularity_stab", "infinity_dagger_singularity_backstab"}) {
            registerSkill(skill.startsWith("infinity_dagger") ? "infinity_dagger" : "infinity_blade", skill, new InfinityWeaponAnimation(skill));
        }
        registerSkill("yeti_tooth", "yeti_tooth_mark", new YetiToothAnimation(false));
        registerSkill("yeti_tooth", "yeti_tooth_spine", new YetiToothAnimation(true));
        registerSkill("meowmere", "meowmere_shot", new MeowmereAnimation(false));
        registerSkill("meowmere", "meowmere_symphony", new MeowmereAnimation(true));
        registerSkill("obsidian_edge", "obsidian_resonance", new NoOpWeaponSkillAnimation());
        registerSkill("obsidian_edge", "obsidian_crack", new MineralWeaponAnimation("obsidian_crack"));
        registerSkill("ossified_blade", "ossified_mark", new MineralWeaponAnimation("ossified_mark"));
        registerSkill("ossified_blade", "ossified_execution", new MineralWeaponAnimation("ossified_execution"));
        registerWeapon("obsidian_edge", DEFAULT_ANIMATION);
        registerWeapon("ossified_blade", DEFAULT_ANIMATION);
        // Meowmere keeps the common rest pose.
        registerWeapon("meowmere", DEFAULT_ANIMATION);
    }

    private WeaponSkillAnimationRegistry() {}

    public static void registerSkill(String weaponId, String skillId, WeaponSkillAnimation animation) {
        SKILL_ANIMATIONS.put(key(weaponId, skillId), animation);
    }

    public static void registerWeapon(String weaponId, WeaponSkillAnimation animation) {
        WEAPON_ANIMATIONS.put(weaponId, animation);
    }

    public static WeaponSkillAnimation getAnimation(@Nullable String weaponId, @Nullable String skillId) {
        if (weaponId != null && skillId != null) {
            WeaponSkillAnimation skillAnim = SKILL_ANIMATIONS.get(key(weaponId, skillId));
            if (skillAnim != null) {
                return skillAnim;
            }
        }
        if (weaponId != null) {
            WeaponSkillAnimation weaponAnim = WEAPON_ANIMATIONS.get(weaponId);
            if (weaponAnim != null) {
                return weaponAnim;
            }
        }
        return DEFAULT_ANIMATION;
    }

    private static String key(String weaponId, String skillId) {
        return weaponId + ":" + skillId;
    }
}
