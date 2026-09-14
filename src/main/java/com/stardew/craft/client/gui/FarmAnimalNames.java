package com.stardew.craft.client.gui;

/** Shared naming pages use the existing source-backed SDV name generator. */
public final class FarmAnimalNames {
    private FarmAnimalNames() {}

    public static String next() {
        return SdvAnimalNameGenerator.randomName(
                net.minecraft.client.Minecraft.getInstance().getLanguageManager().getSelected(),
                java.util.concurrent.ThreadLocalRandom.current());
    }
}
