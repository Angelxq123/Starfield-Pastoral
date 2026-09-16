package com.stardew.craft.pet;

import com.stardew.craft.api.v1.pet.*;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** Runtime identity wrapper; missing addon definitions keep their exact saved ID without a substitute pet. */
public final class PetVariant {
    private static final ConcurrentHashMap<ResourceLocation, PetVariant> KNOWN = new ConcurrentHashMap<>();
    public static final PetVariant CAT0 = required("stardewcraft:cat0"), CAT1 = required("stardewcraft:cat1"), CAT2 = required("stardewcraft:cat2"), CAT3 = required("stardewcraft:cat3"), CAT4 = required("stardewcraft:cat4"),
            DOG0 = required("stardewcraft:dog0"), DOG1 = required("stardewcraft:dog1"), DOG2 = required("stardewcraft:dog2"), DOG3 = required("stardewcraft:dog3"), DOG4 = required("stardewcraft:dog4"),
            TURTLE0 = required("stardewcraft:turtle0"), TURTLE1 = required("stardewcraft:turtle1");
    private final String id;
    private PetVariant(String id) { this.id = id; }
    public String id() { return id; }
    public boolean available() { return definition().isPresent(); }
    private Optional<StardewPetBreedDefinition> definition() { var key = key(id); return key == null ? Optional.empty() : StardewPets.breed(key); }
    public StardewPetBreedDefinition breed() { return definition().orElseThrow(() -> new IllegalStateException("Unavailable pet " + id)); }
    public StardewPetSpeciesDefinition species() { return StardewPets.species(breed().species()).orElseThrow(); }
    public Component label() { return available() ? Component.translatable(breed().translationKey()) : Component.translatable("pet.stardewcraft.unavailable"); }
    public int price() { return breed().price(); }
    public boolean initial() { return breed().initial(); }
    public boolean adoptable() { return breed().adoptable(); }
    public boolean wearsHat() { return species().wearsHat(); }
    public double bedChance() { return species().bedChance(); }
    public double stride(boolean sprint) { return sprint ? breed().sprintStride() : breed().walkStride(); }
    public static PetVariant[] values() { return StardewPets.breeds().stream().map(d -> required(d.id().toString())).toArray(PetVariant[]::new); }
    public static List<PetVariant> initialChoices() { return java.util.Arrays.stream(values()).filter(PetVariant::initial).toList(); }
    public static Optional<PetVariant> find(String id) {
        var key = key(id);
        if (key == null || StardewPets.breed(key).isEmpty()) return Optional.empty();
        return Optional.of(KNOWN.computeIfAbsent(key, k -> new PetVariant(k.toString())));
    }
    public static PetVariant fromSaved(String id) { return find(id).orElseGet(() -> new PetVariant(id)); }
    private static PetVariant required(String id) { return find(id).orElseThrow(); }
    private static ResourceLocation key(String id) { return id == null || !id.contains(":") ? null : ResourceLocation.tryParse(id); }
    @Override public String toString() { return id; }
}
