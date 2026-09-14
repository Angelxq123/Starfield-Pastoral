package com.stardew.craft.monster;

import net.minecraft.util.RandomSource;
import java.util.ArrayList;
import java.util.List;

/** GreenSlime.getExtraDropItems color branches, in original else-if order. */
public final class SlimeLootRules {
    private SlimeLootRules() {}
    public record Drop(String id, int count) {}
    public static List<Drop> colorDrops(int rgb, int specialNumber, boolean firstGeneration,
                                        RandomSource random, RandomSource positional) {
        int r = rgb >> 16 & 255, g = rgb >> 8 & 255, b = rgb & 255;
        var out = new ArrayList<Drop>();
        if (r >= 50 && r <= 100 && g >= 25 && g <= 50 && b <= 25) {
            out.add(new Drop("388", 3 + random.nextInt(4)));
            if (random.nextDouble() < .1) out.add(new Drop("709", 1));
        } else if (r < 80 && g < 80 && b < 80) {
            out.add(new Drop("382", 1));
            if (positional.nextDouble() < .05) out.add(new Drop("553", 1));
            if (positional.nextDouble() < .05) out.add(new Drop("539", 1));
        } else if (r > 200 && g > 180 && b < 50) out.add(new Drop("384", 2));
        else if (r > 220 && g > 90 && g < 150 && b < 50) out.add(new Drop("378", 2));
        else if (r > 230 && g > 230 && b > 230) {
            if (r % 2 == 1) { out.add(new Drop("338", 1)); if (g % 2 == 1) out.add(new Drop("338", 1)); }
            else out.add(new Drop("380", 1));
            if (r % 2 == 0 && g % 2 == 0 && b % 2 == 0 || rgb == 0xFFFFFF) out.add(new Drop("72", 1));
        } else if (r > 150 && g > 150 && b > 150) out.add(new Drop("390", 2));
        else if (r > 150 && b > 180 && g < 50 && specialNumber % (firstGeneration ? 4 : 2) == 0) {
            out.add(new Drop("386", 2));
            if (firstGeneration && random.nextDouble() < .005) out.add(new Drop("485", 1));
        }
        return List.copyOf(out);
    }
}
