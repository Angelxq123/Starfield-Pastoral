package com.example.petaddon;

import com.stardew.craft.api.v1.pet.StardewPetBehavior;
import com.stardew.craft.api.v1.pet.StardewPetBreedDefinition;
import com.stardew.craft.api.v1.pet.StardewPetSpeciesDefinition;
import com.stardew.craft.api.v1.pet.StardewPets;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.common.Mod;

/** This source set is outside the core mod. It supplies a selectable companion only when installed. */
@Mod(ExamplePetAddon.MOD_ID)
public final class ExamplePetAddon {
    public static final String MOD_ID = "pet_api_example";
    public ExamplePetAddon() {
        var cat = StardewPets.species(ResourceLocation.parse("stardewcraft:cat")).orElseThrow();
        var appearance = StardewPets.breed(ResourceLocation.parse("stardewcraft:cat0")).orElseThrow();
        StardewPetBehavior behavior = new StardewPetBehavior(cat.behavior().clips(), cat.behavior().states(), cat.behavior().postureExits());
        var species = new StardewPetSpeciesDefinition(id("companion"), .6f, .9f, true, .5,
                behavior, 0, List.of(), cat.ambientSound(), cat.contentSound(), .6f, 1);
        StardewPets.registerSpecies(species);
        // Sharing an existing rig is valid. A finished custom pet points these three resources at its own namespace.
        StardewPets.registerBreed(new StardewPetBreedDefinition(id("forest_companion"), species.id(),
                "pet.pet_api_example.forest_companion", appearance.model(), appearance.texture(), appearance.icon(),
                true, false, 0, 4, 4));
    }
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath(MOD_ID, path); }
}
