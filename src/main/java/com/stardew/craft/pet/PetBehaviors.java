package com.stardew.craft.pet;

import com.stardew.craft.api.v1.pet.StardewPetBehavior;
import java.util.List;
import net.minecraft.util.RandomSource;

/** Runtime evaluator of immutable registered species graphs. */
public final class PetBehaviors {
    private PetBehaviors() {}
    public static StardewPetBehavior get(PetVariant variant) { return variant.species().behavior(); }
    public static int ticks(PetVariant variant, String clip) { return Math.max(1, (int) Math.ceil(get(variant).clips().get(clip) * 20)); }
    public static String choose(List<StardewPetBehavior.Choice> choices, boolean indoors, RandomSource random) {
        double sum = choices.stream().filter(c -> !indoors || !c.outside()).mapToDouble(StardewPetBehavior.Choice::weight).sum();
        if (sum <= 0) return null;
        double value = random.nextDouble() * sum;
        for (var choice : choices) if (!indoors || !choice.outside()) { value -= choice.weight(); if (value < 0) return choice.state(); }
        throw new IllegalStateException("Pet choice weights");
    }
}
