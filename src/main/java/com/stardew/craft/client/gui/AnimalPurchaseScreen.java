package com.stardew.craft.client.gui;

import com.stardew.craft.client.animal.LivestockShopScreen;
import com.stardew.craft.network.payload.OpenAnimalPurchaseScreenPayload;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

/** Public animal-shop API adapts to the same folio, preserving arbitrary registered IDs. */
public final class AnimalPurchaseScreen extends LivestockShopScreen {
    private final OpenAnimalPurchaseScreenPayload source;

    public AnimalPurchaseScreen(OpenAnimalPurchaseScreenPayload source) {
        super(snapshot(source));
        this.source = source;
    }

    static CompoundTag describe(OpenAnimalPurchaseScreenPayload.AnimalOption a) {
        var row = new CompoundTag();
        row.putString("Species", a.animalTypeId());
        row.putString("DisplayName", a.displayName());
        row.putString("Family", a.family());
        row.putInt("MinTier", a.requiredTier());
        row.putInt("Price", a.price());
        row.putString("Texture", a.shopTextureId());
        row.putInt("TextureWidth", a.shopTextureWidth());
        row.putInt("TextureHeight", a.shopTextureHeight());
        return row;
    }

    private static CompoundTag snapshot(OpenAnimalPurchaseScreenPayload source) {
        var tag = new CompoundTag();
        tag.putInt("Money", source.playerMoney());
        var catalog = new ListTag();
        for (var a : source.animalOptions()) {
            var row = describe(a);
            boolean home =
                    source.buildingOptions().stream()
                            .anyMatch(
                                    h ->
                                            h.family().equals(a.family())
                                                    && h.tier() >= a.requiredTier()
                                                    && h.animalCount() < h.capacity());
            boolean affordable = source.incubatorMode() || source.playerMoney() >= a.price();
            row.putBoolean("Available", a.unlocked() && home && affordable);
            row.putString(
                    "ReasonKey",
                    !a.unlocked()
                            ? a.lockReasonKey()
                            : !home
                                    ? "livestock.stardewcraft.no_home"
                                    : !affordable ? "livestock.stardewcraft.money" : "");
            catalog.add(row);
        }
        tag.put("Catalog", catalog);
        return tag;
    }

    static java.util.List<CompoundTag> homes(
            OpenAnimalPurchaseScreenPayload source,
            OpenAnimalPurchaseScreenPayload.AnimalOption animal) {
        return source.buildingOptions().stream()
                .filter(
                        h ->
                                h.family().equals(animal.family())
                                        && h.tier() >= animal.requiredTier())
                .map(
                        h -> {
                            var row = new CompoundTag();
                            row.putString("StableId", h.buildingId());
                            row.putString("BuildingName", h.displayName());
                            row.putString(
                                    "Family",
                                    h.family().contains(":")
                                            ? h.family()
                                            : "stardewcraft:" + h.family());
                            row.putInt("Tier", h.tier());
                            row.putInt("Used", h.animalCount());
                            row.putInt("Capacity", h.capacity());
                            return row;
                        })
                .toList();
    }

    @Override
    protected void choose(CompoundTag row) {
        var animal =
                source.animalOptions().stream()
                        .filter(a -> a.animalTypeId().equals(row.getString("Species")))
                        .findFirst()
                        .orElseThrow();
        minecraft.setScreen(
                new BuildingChoiceScreen(
                        this,
                        homes(source, animal),
                        describe(animal),
                        home ->
                                minecraft.setScreen(
                                        new AnimalPurchaseBuildingScreen(
                                                this, source, animal, home))));
    }
}
