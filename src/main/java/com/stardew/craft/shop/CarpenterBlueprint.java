package com.stardew.craft.shop;

import com.stardew.craft.api.v1.building.StardewBuildingBlueprint;

import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Data class representing a single building blueprint in Robin's carpenter menu. Mirrors SDV
 * CarpenterMenu.BlueprintEntry.
 */
public record CarpenterBlueprint(
        String id,
        String displayNameKey,
        String descriptionKey,
        int cost,
        List<MaterialEntry> materials,
        String resultItemId,
        boolean isUpgrade,
        int previewCanvasSize,
        boolean magicalConstruction,
        net.minecraft.nbt.CompoundTag presentation) {
    public CarpenterBlueprint {
        materials = List.copyOf(materials);
        presentation =
                presentation == null ? new net.minecraft.nbt.CompoundTag() : presentation.copy();
    }

    public CarpenterBlueprint(
            String id,
            String displayNameKey,
            String descriptionKey,
            int cost,
            List<MaterialEntry> materials,
            String resultItemId,
            boolean isUpgrade,
            int previewCanvasSize,
            boolean magicalConstruction) {
        this(
                id,
                displayNameKey,
                descriptionKey,
                cost,
                materials,
                resultItemId,
                isUpgrade,
                previewCanvasSize,
                magicalConstruction,
                new net.minecraft.nbt.CompoundTag());
    }

    private static net.minecraft.nbt.CompoundTag presentation(StardewBuildingBlueprint blueprint) {
        var tag = new net.minecraft.nbt.CompoundTag();
        var id = blueprint.id();
        int tier = 1;
        var result =
                net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                        blueprint.definition().resultItem());
        if (result instanceof com.stardew.craft.building.runtime.BuildingUpgradePermitItem permit) {
            id = permit.family();
            tier = permit.targetTier();
        }
        if (com.stardew.craft.building.runtime.UtilityBuildings.managed(id)) {
            tag.putInt("ManagerPrice", com.stardew.craft.building.runtime.PrefabDefinitions.get(id).managerPrice());
            tag.putBoolean("ChooseRoute", !blueprint.definition().upgrade() && !com.stardew.craft.building.runtime.FishPondPrefabs.isPond(id));
            var rules=com.stardew.craft.animal.model.AnimalBuildingTierDefinitions.find(id.getNamespace().equals("stardewcraft")?id.getPath():id.toString(),tier);
            tag.putInt("Capacity", rules==null?0:rules.capacity());
            tag.putInt("Tier", tier);tag.putString("ArtFamily",id.toString());
            for (var animal : com.stardew.craft.animal.runtime.LivestockSpecies.values())
                if (animal.family().equals(id) && animal.price() > 0) {
                    var portrait = new net.minecraft.nbt.CompoundTag();
                    com.stardew.craft.animal.runtime.LivestockUiData.describe(portrait, animal);
                    tag.put("Animal", portrait);
                    break;
                }
        }
        return tag;
    }

    public Component displayName() {
        return Component.translatable(displayNameKey);
    }

    public Component description() {
        return Component.translatable(descriptionKey);
    }

    public static CarpenterBlueprint from(StardewBuildingBlueprint blueprint) {
        var definition = blueprint.definition();
        return new CarpenterBlueprint(
                blueprint.id().toString(),
                definition.displayNameKey(),
                definition.descriptionKey(),
                definition.money(),
                definition.materials().stream()
                        .map(
                                material ->
                                        new MaterialEntry(
                                                material.item().toString(), material.count()))
                        .toList(),
                definition.resultItem().toString(),
                definition.upgrade(),
                definition.previewCanvasSize(),
                definition.magicalConstruction(),
                presentation(blueprint));
    }

    public record MaterialEntry(String itemId, int count) {}
}
