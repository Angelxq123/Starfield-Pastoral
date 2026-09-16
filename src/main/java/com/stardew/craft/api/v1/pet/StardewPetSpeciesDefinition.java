package com.stardew.craft.api.v1.pet;

import java.util.List;
import java.util.Objects;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;

/** Experimental species rules. Null sound IDs mean silent; models and appearance belong to breeds. */
public record StardewPetSpeciesDefinition(ResourceLocation id, float width, float height, boolean wearsHat,
        double bedChance, StardewPetBehavior behavior, double giftChance, List<Gift> gifts,
        @Nullable ResourceLocation ambientSound, @Nullable ResourceLocation contentSound, float voiceVolume, float voicePitch, StardewPetFeedback feedback) {
    public StardewPetSpeciesDefinition(ResourceLocation id, float width, float height, boolean wearsHat,
            double bedChance, StardewPetBehavior behavior, double giftChance, List<Gift> gifts,
            @Nullable ResourceLocation ambientSound, @Nullable ResourceLocation contentSound, float voiceVolume, float voicePitch) {
        this(id, width, height, wearsHat, bedChance, behavior, giftChance, gifts, ambientSound, contentSound, voiceVolume, voicePitch, StardewPetFeedback.EMPTY);
    }
    public record Gift(String query, int count, int friendship, double weight) {
        public Gift {
            if (query == null || query.isBlank() || count < 1 || friendship < 0 || friendship > 1000
                    || !Double.isFinite(weight) || weight <= 0) throw new IllegalArgumentException("Pet gift");
        }
    }
    public StardewPetSpeciesDefinition {
        Objects.requireNonNull(id); Objects.requireNonNull(behavior); Objects.requireNonNull(feedback);
        if (!behavior.states().keySet().containsAll(feedback.states().keySet())) throw new IllegalArgumentException("Unknown pet feedback state"); gifts = List.copyOf(gifts);
        if (!Float.isFinite(width) || width <= 0 || !Float.isFinite(height) || height <= 0
                || !Double.isFinite(bedChance) || bedChance < 0 || bedChance > 1
                || !Double.isFinite(giftChance) || giftChance < 0 || giftChance > 1
                || !Float.isFinite(voiceVolume) || voiceVolume < 0 || !Float.isFinite(voicePitch) || voicePitch <= 0) throw new IllegalArgumentException("Pet species dimensions/probabilities");
    }
}
