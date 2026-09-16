package com.stardew.craft.item.artisan;

import com.stardew.craft.StardewCraft;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

@EventBusSubscriber(modid = StardewCraft.MODID)
public final class ArtisanSystem {
    private ArtisanSystem() {
    }

    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new ArtisanRecipeDataManager.ReloadListener());
        event.addListener(new com.stardew.craft.production.MachineProductionData.ReloadListener());
    }
}
