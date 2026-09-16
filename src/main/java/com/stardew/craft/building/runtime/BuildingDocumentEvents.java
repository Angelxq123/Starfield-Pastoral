package com.stardew.craft.building.runtime;

import com.stardew.craft.StardewCraft;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/** Documents own right-clicks; a chest/manager underneath must not steal the input. */
@EventBusSubscriber(modid = StardewCraft.MODID)
public final class BuildingDocumentEvents {
    private BuildingDocumentEvents() {}
    @SubscribeEvent public static void use(PlayerInteractEvent.RightClickBlock event) {
        var item = event.getItemStack().getItem();
        if (item instanceof BuildingBlueprintItem || item instanceof BuildingUpgradePermitItem) event.setUseBlock(TriState.FALSE);
    }
}
