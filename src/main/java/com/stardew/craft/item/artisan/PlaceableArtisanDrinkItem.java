package com.stardew.craft.item.artisan;

import net.minecraft.world.item.Item;

/** An artisan drink with a registered bottle model and full-stack placement. */
public class PlaceableArtisanDrinkItem extends ArtisanDrinkItem {
    private final String placedBlockId;

    public PlaceableArtisanDrinkItem(String placedBlockId, int sellPrice, int energy, int health,
                                    int speedBonus, int speedDurationTicks, boolean supportsQuality,
                                    Item.Properties properties) {
        super(sellPrice, energy, health, speedBonus, speedDurationTicks, supportsQuality, properties);
        this.placedBlockId = placedBlockId;
    }

    public String getPlacedBlockId() {
        return placedBlockId;
    }

    @Override
    public net.minecraft.world.InteractionResult useOn(net.minecraft.world.item.context.UseOnContext context) {
        if (context.getPlayer() == null || !context.getPlayer().isShiftKeyDown()) {
            return super.useOn(context);
        }
        return com.stardew.craft.item.cooking.PlacedFoodPlacement.place(context);
    }

}
