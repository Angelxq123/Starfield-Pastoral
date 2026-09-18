package com.stardew.craft.event;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.block.decor.SupplyCrateBlock;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;

@EventBusSubscriber(modid = StardewCraft.MODID)
public final class SupplyCrateEvents {
    private SupplyCrateEvents() {}

    @SubscribeEvent
    public static void beforeBreak(BlockEvent.BreakEvent event) {
        if (event.getState().getBlock() instanceof SupplyCrateBlock && !event.getPlayer().isCreative()
                && !SupplyCrateBlock.isHeavyHitter(event.getPlayer().getMainHandItem())) event.setCanceled(true);
    }
}
