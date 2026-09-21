package com.stardew.craft.monster;

import net.minecraft.util.RandomSource;
import java.util.ArrayList;
import java.util.List;

/** RockGolem(position, difficultyMod) and its two independent death-time loot branches. */
public final class FarmGolemRules {
    private FarmGolemRules() {}
    public record Drop(String id, int count) {}

    public static boolean iridium(int combatLevel, boolean wildernessFarm, RandomSource random) {
        return combatLevel >= 9 && random.nextDouble() < .5 && wildernessFarm;
    }

    public static List<String> constructorDrops(int difficulty, boolean iridium, RandomSource random) {
        var out = new ArrayList<String>();
        if (difficulty >= 5 && random.nextDouble() < .05) out.add("omni_geode");
        if (difficulty >= 5 && random.nextDouble() < .2) out.add("mixed_seeds");
        if (difficulty >= 10 && random.nextDouble() < .01) out.add("iridium_ore");
        if (difficulty >= 10 && random.nextDouble() < .01) out.add("iridium_ore");
        if (difficulty >= 10 && random.nextDouble() < .001) out.add("prismatic_shard");
        if (iridium) {
            if (random.nextDouble() < .03) out.add("iridium_bar");
            if (random.nextDouble() < .03) out.add("iridium_bar");
        }
        return out;
    }

    public static List<Drop> extraDrops(boolean iridium, int season, int day, double luck, RandomSource random) {
        var out = new ArrayList<Drop>();
        if (!iridium) {
            if (random.nextDouble() <= .0001) out.add(new Drop("(H)40", 1));
            else if (season == 0 && random.nextDouble() < .0825) {
                int count = random.nextInt(4) + 2;
                for (int i = 0; i < count; i++) out.add(new Drop("273", 1));
            }
            return out;
        }
        while (random.nextDouble() < .5) {
            // Utility.getRaccoonSeedForCurrentTimeOfYear still rolls quantity before overriding it to one.
            random.nextInt(2);
            while (random.nextDouble() < .1 + luck) { }
            int seedSeason = day > (season == 0 ? 23 : 20) ? (season + 1) % 4 : season;
            out.add(new Drop(new String[]{"CarrotSeeds", "SummerSquashSeeds", "BroccoliSeeds", "PowdermelonSeeds"}[seedSeason], 1));
        }
        while (random.nextDouble() < .2) out.add(new Drop("386", 1));
        if (random.nextDouble() < .01) out.add(new Drop("SkillBook_" + random.nextInt(5), 1));
        if (random.nextDouble() < .001) out.add(new Drop("527", 1));
        if (random.nextDouble() <= .0002) out.add(new Drop("(H)40", 1));
        return out;
    }
}
