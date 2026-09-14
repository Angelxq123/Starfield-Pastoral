package com.stardew.craft.api.v1.pet;

import java.util.Objects;
import net.minecraft.resources.ResourceLocation;

/** Experimental selectable breed. Resource IDs include full paths, such as textures/entity/raccoon.png. */
public record StardewPetBreedDefinition(ResourceLocation id, ResourceLocation species, String translationKey,
        ResourceLocation model, ResourceLocation texture, ResourceLocation icon, boolean initial, boolean adoptable,
        int price, double walkStride, double sprintStride, ResourceLocation barkOverride, float voicePitch) {
    public StardewPetBreedDefinition(ResourceLocation id, ResourceLocation species, String translationKey,
            ResourceLocation model, ResourceLocation texture, ResourceLocation icon, boolean initial, boolean adoptable,
            int price, double walkStride, double sprintStride) {
        this(id, species, translationKey, model, texture, icon, initial, adoptable, price, walkStride, sprintStride, null, 1);
    }
    public StardewPetBreedDefinition {
        Objects.requireNonNull(id); Objects.requireNonNull(species); Objects.requireNonNull(model); Objects.requireNonNull(texture); Objects.requireNonNull(icon);
        if (id.toString().length() > 256 || translationKey == null || translationKey.isBlank()
                || !model.getPath().endsWith(".json.gz") || !texture.getPath().endsWith(".png") || !icon.getPath().endsWith(".png")
                || !Float.isFinite(voicePitch) || voicePitch <= 0 || price < 0 || !Double.isFinite(walkStride) || walkStride <= 0 || !Double.isFinite(sprintStride) || sprintStride <= 0)
            throw new IllegalArgumentException("Pet breed metadata/resources/stride");
    }
}
